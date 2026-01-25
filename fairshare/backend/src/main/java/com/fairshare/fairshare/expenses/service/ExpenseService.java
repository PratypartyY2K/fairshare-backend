package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.common.NotFoundException;
import com.fairshare.fairshare.expenses.api.CreateExpenseRequest;
import com.fairshare.fairshare.expenses.api.ExpenseResponse;
import com.fairshare.fairshare.expenses.api.LedgerResponse;
import com.fairshare.fairshare.groups.GroupMemberRepository;
import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import com.fairshare.fairshare.expenses.model.ExpenseEvent;
import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.fairshare.fairshare.expenses.api.SettlementResponse;
import com.fairshare.fairshare.expenses.api.ConfirmSettlementsRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final ExpenseParticipantRepository participantRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final ConfirmedTransferRepository confirmedTransferRepo;
    private final ExpenseEventRepository eventRepo;

    public ExpenseService(
            ExpenseRepository expenseRepo,
            ExpenseParticipantRepository participantRepo,
            LedgerEntryRepository ledgerRepo,
            GroupMemberRepository groupMemberRepo,
            ConfirmedTransferRepository confirmedTransferRepo,
            ExpenseEventRepository eventRepo
    ) {
        this.expenseRepo = expenseRepo;
        this.participantRepo = participantRepo;
        this.ledgerRepo = ledgerRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.confirmedTransferRepo = confirmedTransferRepo;
        this.eventRepo = eventRepo;
    }

    // Currency normalization helper: enforce scale=2 and HALF_UP rounding, non-null, non-negative
    private static BigDecimal normalizeCurrency(BigDecimal v) {
        if (v == null) throw new IllegalArgumentException("Amount cannot be null");
        BigDecimal norm = v.setScale(2, RoundingMode.HALF_UP);
        if (norm.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Amount must be non-negative");
        return norm;
    }

    // Backwards-compatible legacy method delegates to the new request-based API
    @Transactional
    public ExpenseResponse createExpense(
            Long groupId,
            String description,
            BigDecimal amount,
            Long payerUserId,
            List<Long> participantUserIds
    ) {
        CreateExpenseRequest req = new CreateExpenseRequest(description, amount, payerUserId, participantUserIds);
        return createExpense(groupId, req);
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest req) {
        return createExpense(groupId, req, null);
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest req, String idempotencyKey) {
        // If idempotency key provided, return existing expense if one was already created for this group+key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = expenseRepo.findByGroupIdAndIdempotencyKey(groupId, idempotencyKey);
            if (existing.isPresent()) {
                Expense ex = existing.get();
                // rebuild shares map from participantRepo
                Map<Long, BigDecimal> shares = new LinkedHashMap<>();
                for (ExpenseParticipant p : participantRepo.findByExpenseId(ex.getId())) {
                    shares.put(p.getUserId(), p.getShareAmount());
                }
                return toExpenseResponse(ex, shares);
            }
        }

        // normalize and validate total amount (currency contract: scale 2, HALF_UP)
        if (req.amount() == null) throw new BadRequestException("Amount must be provided");
        BigDecimal totalAmount = normalizeCurrency(req.amount());

        // participants
        List<Long> participantUserIds = req.participantUserIds();
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            participantUserIds = groupMemberRepo.findByGroupId(groupId).stream()
                    .map(gm -> gm.getUser().getId())
                    .toList();
        }

        // validator: participants unique
        LinkedHashSet<Long> uniq = new LinkedHashSet<>(participantUserIds);
        if (uniq.size() != participantUserIds.size()) {
            throw new BadRequestException("Participants must be unique");
        }

        // ensure payer and participants are members
        Long payer = req.payerUserId();
        requireMember(groupId, payer);
        for (Long uid : participantUserIds) requireMember(groupId, uid);

        // ensure participants includes payer (will be added later)
        LinkedHashSet<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payer);

        if (participants.isEmpty()) throw new BadRequestException("At least one participant is required");

        List<Long> ids = new ArrayList<>(participants);

        // Determine shares mapping based on requested split mode
        Map<Long, BigDecimal> sharesMap = null;

        // Mode precedence: exactAmounts > percentages > shares > equal
        if (req.getExactAmounts() != null) {
            var exact = req.getExactAmounts();
            if (exact.size() != participantUserIds.size()) {
                throw new BadRequestException("exactAmounts length must match participantUserIds length");
            }
            // map each participantUserIds order to exact amount, then include payer if not present
            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal v = normalizeCurrency(exact.get(i));
                tmp.put(participantUserIds.get(i), v);
            }
            // if payer wasn't in participantUserIds, add their share as 0 (they'll be included in participants list later)
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // validation: all non-negative and sum to amount within tolerance 0.01
            BigDecimal sum = tmp.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tol = new BigDecimal("0.01");
            if (sum.subtract(totalAmount).abs().compareTo(tol) > 0) {
                throw new BadRequestException("Exact amounts must sum to total amount within $0.01 tolerance");
            }

            // If there is minor rounding difference, adjust by distributing leftover cents stably
            sharesMap = distributeLeftover(tmp, totalAmount);

        } else if (req.getPercentages() != null) {
            var pct = req.getPercentages();
            if (pct.size() != participantUserIds.size()) {
                throw new BadRequestException("percentages length must match participantUserIds length");
            }
            BigDecimal sumPct = pct.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pctTol = new BigDecimal("0.01");
            if (sumPct.subtract(new BigDecimal("100")).abs().compareTo(pctTol) > 0) {
                throw new BadRequestException("Percentages must sum to 100% within 0.01 tolerance");
            }

            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal share = totalAmount.multiply(pct.get(i)).divide(new BigDecimal("100"));
                tmp.put(participantUserIds.get(i), share.setScale(2, RoundingMode.DOWN)); // floor to 2 decimals
            }

            // ensure payer present
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            sharesMap = distributeLeftover(tmp, totalAmount);

        } else if (req.getShares() != null) {
            var s = req.getShares();
            if (s.size() != participantUserIds.size()) {
                throw new BadRequestException("shares length must match participantUserIds length");
            }
            int total = s.stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) throw new BadRequestException("Sum of shares must be positive");

            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal fraction = new BigDecimal(s.get(i)).divide(new BigDecimal(total), 10, RoundingMode.HALF_UP);
                BigDecimal share = totalAmount.multiply(fraction).setScale(2, RoundingMode.DOWN);
                tmp.put(participantUserIds.get(i), share);
            }
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            sharesMap = distributeLeftover(tmp, totalAmount);

        } else {
            // equal split
            sharesMap = equalSplit(totalAmount, ids);
        }

        // Save expense (use idempotencyKey-aware constructor if provided)
        Expense expense;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            expense = new Expense(groupId, payer, req.description().trim(), totalAmount, idempotencyKey);
        } else {
            expense = new Expense(groupId, payer, req.description().trim(), totalAmount);
        }
        // ensure not null and explicitly set to false so that findByGroupIdAndVoidedFalse picks it up
        expense.setVoided(false);
        expense = expenseRepo.save(expense);

        for (var e : sharesMap.entrySet()) {
            // ensure stored shares are scale-2
            BigDecimal normalizedShare = normalizeCurrency(e.getValue());
            participantRepo.save(new ExpenseParticipant(expense.getId(), e.getKey(), normalizedShare));
        }

        // Ledger updates
        ledger(groupId, payer).add(totalAmount);
        for (var e : sharesMap.entrySet()) {
            ledger(groupId, e.getKey()).add(e.getValue().negate());
        }

        // persist creation event
        String createdPayload = String.format("{\"expenseId\":%d,\"amount\":%s}", expense.getId(), expense.getAmount().toString());
        eventRepo.save(new ExpenseEvent(groupId, expense.getId(), "ExpenseCreated", createdPayload));

        return toExpenseResponse(expense, sharesMap);
    }

    /**
     * Distribute leftover cents so that the final rounded sums equal totalAmount.
     * tmpMap is mapping from userId->floor(amount to 2 decimals) or possibly exact set
     */
    private Map<Long, BigDecimal> distributeLeftover(Map<Long, BigDecimal> tmpMap, BigDecimal totalAmount) {
        // Work on a linked map to preserve insertion order (participantUserIds order), but requirement asks to distribute by userId ascending
        // We'll create a list sorted by userId ascending as requested
        List<Map.Entry<Long, BigDecimal>> entries = new ArrayList<>(tmpMap.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        // ensure all entries are scaled to 2
        Map<Long, BigDecimal> scaled = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : entries) {
            scaled.put(e.getKey(), normalizeCurrency(e.getValue()));
        }

        BigDecimal sum = scaled.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = totalAmount.setScale(2, RoundingMode.HALF_UP).subtract(sum).setScale(2, RoundingMode.HALF_UP);

        int cents = diff.movePointRight(2).intValueExact(); // may be negative or positive

        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : entries) out.put(e.getKey(), scaled.get(e.getKey()));

        int i = 0;
        int n = entries.size();
        while (cents > 0) {
            Long id = entries.get(i % n).getKey();
            out.put(id, out.get(id).add(new BigDecimal("0.01")));
            i++;
            cents--;
        }
        while (cents < 0) {
            Long id = entries.get(i % n).getKey();
            out.put(id, out.get(id).subtract(new BigDecimal("0.01")));
            i++;
            cents++;
        }

        // final normalization
        out.replaceAll((k, v) -> normalizeCurrency(v));

        return out;
    }

    @Transactional
    public LedgerResponse getLedger(Long groupId) {
        var entries = ledgerRepo.findByGroupIdOrderByUserIdAsc(groupId).stream()
                .map(e -> new LedgerResponse.Entry(e.getUserId(), e.getNetBalance()))
                .toList();
        return new LedgerResponse(entries);
    }

    @Transactional
    public List<ExpenseResponse> listExpenses(Long groupId) {
        var expenses = expenseRepo.findByGroupIdOrderByCreatedAtDesc(groupId);

        List<ExpenseResponse> out = new ArrayList<>();
        for (Expense ex : expenses) {
            if (ex.isVoided()) continue; // skip voided expenses (handle nullable DB column safely)
            Map<Long, BigDecimal> shares = new LinkedHashMap<>();
            for (ExpenseParticipant p : participantRepo.findByExpenseId(ex.getId())) {
                shares.put(p.getUserId(), p.getShareAmount());
            }
            out.add(toExpenseResponse(ex, shares));
        }
        return out;
    }

    private ExpenseResponse toExpenseResponse(Expense expense, Map<Long, BigDecimal> shares) {
        var splits = shares.entrySet().stream()
                .map(x -> new ExpenseResponse.Split(x.getKey(), x.getValue()))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getGroupId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getPayerUserId(),
                expense.getCreatedAt(),
                splits
        );
    }

    @Transactional
    public void confirmSettlements(Long groupId, ConfirmSettlementsRequest req) {
        if (req == null || req.getTransfers() == null || req.getTransfers().isEmpty()) return;

        String confirmationId = req.getConfirmationId();
        if (confirmationId != null && !confirmationId.isBlank()) {
            var existing = confirmedTransferRepo.findByGroupIdAndConfirmationId(groupId, confirmationId);
            if (existing.isPresent()) return; // idempotent: already processed
        }

        for (var t : req.getTransfers()) {
            if (t.getAmount() == null || t.getAmount().signum() <= 0) {
                throw new BadRequestException("Transfer amount must be positive");
            }
            BigDecimal amt = normalizeCurrency(t.getAmount());
            if (amt.signum() <= 0) throw new BadRequestException("Transfer amount must be positive and non-zero");

            Long from = t.getFromUserId();
            Long to = t.getToUserId();
            // membership validation
            requireMember(groupId, from);
            requireMember(groupId, to);

            LedgerEntry fromEntry = ledger(groupId, from);
            LedgerEntry toEntry = ledger(groupId, to);

            fromEntry.add(amt);
            toEntry.add(amt.negate());

            ledgerRepo.save(fromEntry);
            ledgerRepo.save(toEntry);

            // persist confirmed transfer for historical tracking (store normalized amount + confirmation id if provided)
            ConfirmedTransfer ct = (confirmationId != null && !confirmationId.isBlank())
                    ? new ConfirmedTransfer(groupId, from, to, amt, confirmationId)
                    : new ConfirmedTransfer(groupId, from, to, amt);
            confirmedTransferRepo.save(ct);
        }
    }

    @Transactional
    public BigDecimal amountOwed(Long groupId, Long fromUserId, Long toUserId) {
        // Validate members
        requireMember(groupId, fromUserId);
        requireMember(groupId, toUserId);

        // Compute settlements
        SettlementResponse s = getSettlements(groupId);
        BigDecimal total = BigDecimal.ZERO;
        for (var t : s.transfers()) {
            if (t.fromUserId().equals(fromUserId) && t.toUserId().equals(toUserId)) {
                total = total.add(t.amount());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public BigDecimal amountOwedHistorical(Long groupId, Long fromUserId, Long toUserId) {
        // Validate members
        requireMember(groupId, fromUserId);
        requireMember(groupId, toUserId);

        // obligations: sum of share_amount where expense.payer = toUserId and participant = fromUserId
        BigDecimal obligations = participantRepo.sumShareByGroupAndPayerAndUser(groupId, toUserId, fromUserId);

        // payments: sum of confirmed transfers from fromUserId to toUserId
        BigDecimal payments = confirmedTransferRepo.sumConfirmedAmount(groupId, fromUserId, toUserId);

        BigDecimal outstanding = obligations.subtract(payments).setScale(2, RoundingMode.HALF_UP);
        return outstanding;
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberRepo.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("User " + userId + " is not a member of group " + groupId);
        }
    }

    private LedgerEntry ledger(Long groupId, Long userId) {
        return ledgerRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseGet(() -> ledgerRepo.save(new LedgerEntry(groupId, userId)));
    }

    private Map<Long, BigDecimal> equalSplit(BigDecimal amount, List<Long> userIds) {
        int n = userIds.size();

        BigDecimal base = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal totalBase = base.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = amount.subtract(totalBase); // 0.00 to 0.99

        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (Long id : userIds) out.put(id, base);

        int cents = remainder.movePointRight(2).intValueExact();
        for (int i = 0; i < cents; i++) {
            Long id = userIds.get(i % n);
            out.put(id, out.get(id).add(new BigDecimal("0.01")));
        }
        // final normalization
        out.replaceAll((k, v) -> normalizeCurrency(v));
        return out;
    }

    @Transactional
    public SettlementResponse getSettlements(Long groupId) {
        var entries = ledgerRepo.findByGroupIdOrderByUserIdAsc(groupId);

        Map<Long, java.math.BigDecimal> net = new java.util.LinkedHashMap<>();
        for (var e : entries) {
            net.put(e.getUserId(), e.getNetBalance());
        }

        var transfers = SettlementCalculator.compute(net).stream()
                .map(t -> new SettlementResponse.Transfer(t.fromUserId(), t.toUserId(), t.amount()))
                .toList();

        return new SettlementResponse(transfers);
    }

    @Transactional
    public ExpenseResponse updateExpense(Long groupId, Long expenseId, CreateExpenseRequest req) {
        Expense ex = expenseRepo.findById(expenseId).orElseThrow(() -> new NotFoundException("Expense not found"));
        if (!ex.getGroupId().equals(groupId)) throw new BadRequestException("Expense does not belong to group");
        if (ex.isVoided()) throw new BadRequestException("Expense is voided");

        // build current shares
        Map<Long, BigDecimal> oldShares = new LinkedHashMap<>();
        for (ExpenseParticipant p : participantRepo.findByExpenseId(expenseId))
            oldShares.put(p.getUserId(), p.getShareAmount());
        BigDecimal oldTotal = ex.getAmount();

        // Reuse create logic for determining new sharesMap and validations (but don't create new expense in DB here)
        List<Long> participantUserIds = req.participantUserIds();
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            participantUserIds = new ArrayList<>(oldShares.keySet());
        }

        // validate unique and membership
        LinkedHashSet<Long> uniq = new LinkedHashSet<>(participantUserIds);
        if (uniq.size() != participantUserIds.size()) throw new BadRequestException("Participants must be unique");
        Long payer = req.payerUserId();
        requireMember(groupId, payer);
        for (Long uid : participantUserIds) requireMember(groupId, uid);
        LinkedHashSet<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payer);
        List<Long> ids = new ArrayList<>(participants);

        // compute new sharesMap using same precedence logic as createExpense
        Map<Long, BigDecimal> newShares = null;
        BigDecimal totalAmount = normalizeCurrency(req.amount());
        if (req.getExactAmounts() != null) {
            var exact = req.getExactAmounts();
            if (exact.size() != participantUserIds.size())
                throw new BadRequestException("exactAmounts length must match participantUserIds length");
            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++)
                tmp.put(participantUserIds.get(i), normalizeCurrency(exact.get(i)));
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = distributeLeftover(tmp, totalAmount);
        } else if (req.getPercentages() != null) {
            var pct = req.getPercentages();
            if (pct.size() != participantUserIds.size())
                throw new BadRequestException("percentages length must match participantUserIds length");
            BigDecimal sumPct = pct.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            if (sumPct.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0)
                throw new BadRequestException("Percentages must sum to 100% within 0.01 tolerance");
            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++)
                tmp.put(participantUserIds.get(i), totalAmount.multiply(pct.get(i)).divide(new BigDecimal("100")).setScale(2, RoundingMode.DOWN));
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = distributeLeftover(tmp, totalAmount);
        } else if (req.getShares() != null) {
            var s = req.getShares();
            if (s.size() != participantUserIds.size())
                throw new BadRequestException("shares length must match participantUserIds length");
            int total = s.stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) throw new BadRequestException("Sum of shares must be positive");
            Map<Long, BigDecimal> tmp = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal fraction = new BigDecimal(s.get(i)).divide(new BigDecimal(total), 10, RoundingMode.HALF_UP);
                tmp.put(participantUserIds.get(i), totalAmount.multiply(fraction).setScale(2, RoundingMode.DOWN));
            }
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = distributeLeftover(tmp, totalAmount);
        } else {
            newShares = equalSplit(totalAmount, ids);
        }

        // compute ledger deltas: payer delta = newTotal - oldTotal; for each participant delta = -(newShare - oldShare)
        BigDecimal payerDelta = totalAmount.subtract(oldTotal).setScale(2, RoundingMode.HALF_UP);
        LedgerEntry payerEntry = ledger(groupId, payer);
        payerEntry.add(payerDelta);
        ledgerRepo.save(payerEntry);

        // update participant records and per-user ledger deltas
        // build oldShares default zeros
        Map<Long, BigDecimal> oldSharesDefault = new LinkedHashMap<>(oldShares);
        for (Long id : newShares.keySet())
            oldSharesDefault.putIfAbsent(id, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        for (Map.Entry<Long, BigDecimal> en : newShares.entrySet()) {
            Long uid = en.getKey();
            BigDecimal newShare = normalizeCurrency(en.getValue());
            BigDecimal oldShare = oldSharesDefault.getOrDefault(uid, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal delta = oldShare.negate().add(newShare.negate()).negate();
            // Actually ledger on participant stored negative of share, so to adjust ledger we add -(newShare) - (-(oldShare)) = oldShare - newShare
            BigDecimal ledgerDelta = oldShare.subtract(newShare);
            LedgerEntry le = ledger(groupId, uid);
            le.add(ledgerDelta);
            ledgerRepo.save(le);

            // upsert participant record
            participantRepo.deleteByExpenseIdAndUserId(expenseId, uid);
            participantRepo.save(new ExpenseParticipant(expenseId, uid, newShare));
        }

        // update expense record
        ex.setAmount(totalAmount);
        ex.setDescription(req.description().trim());
        expenseRepo.save(ex);

        // persist event
        String payload = String.format("{\"before\":{\"amount\":%s},\"after\":{\"amount\":%s}}", oldTotal.toString(), totalAmount.toString());
        eventRepo.save(new ExpenseEvent(groupId, expenseId, "ExpenseUpdated", payload));

        return toExpenseResponse(ex, newShares);
    }

    @Transactional
    public void voidExpense(Long groupId, Long expenseId) {
        Expense ex = expenseRepo.findById(expenseId).orElseThrow(() -> new NotFoundException("Expense not found"));
        if (!ex.getGroupId().equals(groupId)) throw new BadRequestException("Expense does not belong to group");
        if (ex.isVoided()) return; // idempotent

        // fetch participants
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (ExpenseParticipant p : participantRepo.findByExpenseId(expenseId))
            shares.put(p.getUserId(), p.getShareAmount());

        // reverse ledger entries
        BigDecimal total = ex.getAmount();
        LedgerEntry payerEntry = ledger(groupId, ex.getPayerUserId());
        payerEntry.add(total.negate());
        ledgerRepo.save(payerEntry);

        for (var e : shares.entrySet()) {
            LedgerEntry le = ledger(groupId, e.getKey());
            le.add(e.getValue()); // reverse the negative applied earlier
            ledgerRepo.save(le);
        }

        // mark voided
        ex.setVoided(true);
        expenseRepo.save(ex);

        // persist event
        String payload = String.format("{\"expenseId\":%d,\"amount\":%s}", expenseId, total.toString());
        eventRepo.save(new ExpenseEvent(groupId, expenseId, "ExpenseVoided", payload));
    }
}
