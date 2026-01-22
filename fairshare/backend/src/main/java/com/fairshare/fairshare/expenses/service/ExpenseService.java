package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.expenses.api.CreateExpenseRequest;
import com.fairshare.fairshare.expenses.api.ExpenseResponse;
import com.fairshare.fairshare.expenses.api.LedgerResponse;
import com.fairshare.fairshare.groups.GroupMemberRepository;
import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.fairshare.fairshare.expenses.api.SettlementResponse;

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

    public ExpenseService(
            ExpenseRepository expenseRepo,
            ExpenseParticipantRepository participantRepo,
            LedgerEntryRepository ledgerRepo,
            GroupMemberRepository groupMemberRepo,
            ConfirmedTransferRepository confirmedTransferRepo
    ) {
        this.expenseRepo = expenseRepo;
        this.participantRepo = participantRepo;
        this.ledgerRepo = ledgerRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.confirmedTransferRepo = confirmedTransferRepo;
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
                tmp.put(participantUserIds.get(i), exact.get(i).setScale(2, RoundingMode.HALF_UP));
            }
            // if payer wasn't in participantUserIds, add their share as 0 (they'll be included in participants list later)
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO);

            // validation: all positive (>= 0) and sum to amount within tolerance 0.01
            BigDecimal sum = tmp.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tol = new BigDecimal("0.01");
            if (sum.subtract(req.amount()).abs().compareTo(tol) > 0) {
                throw new BadRequestException("Exact amounts must sum to total amount within $0.01 tolerance");
            }

            // If there is minor rounding difference, adjust by distributing leftover cents stably
            sharesMap = distributeLeftover(tmp, req.amount());

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
                BigDecimal share = req.amount().multiply(pct.get(i)).divide(new BigDecimal("100"));
                tmp.put(participantUserIds.get(i), share.setScale(2, RoundingMode.DOWN)); // floor to 2 decimals
            }

            // ensure payer present
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO);

            sharesMap = distributeLeftover(tmp, req.amount());

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
                BigDecimal share = req.amount().multiply(fraction).setScale(2, RoundingMode.DOWN);
                tmp.put(participantUserIds.get(i), share);
            }
            if (!tmp.containsKey(payer)) tmp.put(payer, BigDecimal.ZERO);
            sharesMap = distributeLeftover(tmp, req.amount());

        } else {
            // equal split
            sharesMap = equalSplit(req.amount(), ids);
        }

        // Save expense
        Expense expense = expenseRepo.save(new Expense(groupId, payer, req.description().trim(), req.amount()));

        for (var e : sharesMap.entrySet()) {
            participantRepo.save(new ExpenseParticipant(expense.getId(), e.getKey(), e.getValue()));
        }

        // Ledger updates
        ledger(groupId, payer).add(req.amount());
        for (var e : sharesMap.entrySet()) {
            ledger(groupId, e.getKey()).add(e.getValue().negate());
        }

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

        BigDecimal sum = entries.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = totalAmount.setScale(2, RoundingMode.HALF_UP).subtract(sum).setScale(2, RoundingMode.HALF_UP);

        int cents = diff.movePointRight(2).intValueExact(); // may be negative or positive

        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : entries) out.put(e.getKey(), e.getValue());

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
    public void confirmSettlements(Long groupId, List<com.fairshare.fairshare.expenses.api.ConfirmSettlementsRequest.Transfer> transfers) {
        if (transfers == null || transfers.isEmpty()) return;

        // Validate transfers and apply them to ledger entries
        for (var t : transfers) {
            if (t.getAmount() == null || t.getAmount().signum() <= 0) {
                throw new BadRequestException("Transfer amount must be positive");
            }
            Long from = t.getFromUserId();
            Long to = t.getToUserId();
            // membership validation
            requireMember(groupId, from);
            requireMember(groupId, to);

            LedgerEntry fromEntry = ledger(groupId, from);
            LedgerEntry toEntry = ledger(groupId, to);

            fromEntry.add(t.getAmount());
            toEntry.add(t.getAmount().negate());

            ledgerRepo.save(fromEntry);
            ledgerRepo.save(toEntry);

            // persist confirmed transfer for historical tracking
            ConfirmedTransfer ct = new ConfirmedTransfer(groupId, from, to, t.getAmount());
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

}
