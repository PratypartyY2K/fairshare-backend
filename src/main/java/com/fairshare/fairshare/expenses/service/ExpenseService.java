package com.fairshare.fairshare.expenses.service;

import com.fairshare.fairshare.auth.ForbiddenException;
import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.common.NotFoundException;
import com.fairshare.fairshare.common.SortUtils;
import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.expenses.model.Expense;
import com.fairshare.fairshare.expenses.ExpenseRepository;
import com.fairshare.fairshare.expenses.model.LedgerEntry;
import com.fairshare.fairshare.expenses.LedgerEntryRepository;
import com.fairshare.fairshare.expenses.ExpenseParticipantRepository;
import com.fairshare.fairshare.expenses.ConfirmedTransferRepository;
import com.fairshare.fairshare.expenses.ExpenseEventRepository;
import com.fairshare.fairshare.expenses.api.*;
import com.fairshare.fairshare.groups.repository.GroupMemberRepository;
import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import com.fairshare.fairshare.expenses.model.ExpenseEvent;
import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import com.fairshare.fairshare.groups.model.GroupMember;
import com.fairshare.fairshare.expenses.SettlementCalculator;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final ExpenseParticipantRepository participantRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final ConfirmedTransferRepository confirmedTransferRepo;
    private final ExpenseEventRepository eventRepo;
    private final EntityManager em;

    public ExpenseService(
            ExpenseRepository expenseRepo,
            ExpenseParticipantRepository participantRepo,
            LedgerEntryRepository ledgerRepo,
            GroupMemberRepository groupMemberRepo,
            ConfirmedTransferRepository confirmedTransferRepo,
            ExpenseEventRepository eventRepo,
            EntityManager em
    ) {
        this.expenseRepo = expenseRepo;
        this.participantRepo = participantRepo;
        this.ledgerRepo = ledgerRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.confirmedTransferRepo = confirmedTransferRepo;
        this.eventRepo = eventRepo;
        this.em = em;
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount cannot be null");
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Amount must be non-negative");
        return normalized;
    }

    @Transactional
    public ExpenseResponse createExpense(Long groupId, Long actorUserId, CreateExpenseRequest req, String idempotencyKey) {
        requireActorMember(groupId, actorUserId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = expenseRepo.findByGroupIdAndIdempotencyKey(groupId, idempotencyKey);
            if (existing.isPresent()) {
                Expense expense = existing.get();
                Map<Long, BigDecimal> shares = new LinkedHashMap<>();
                for (ExpenseParticipant participant : participantRepo.findByExpense_Id(expense.getId())) {
                    shares.put(participant.getUserId(), participant.getShareAmount());
                }
                return toExpenseResponse(expense, shares);
            }
        }

        if (req.amount() == null) throw new BadRequestException("Amount must be provided");
        BigDecimal totalAmount = normalizeAmount(req.amount());

        List<Long> participantUserIds = req.participantUserIds();
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            participantUserIds = groupMemberRepo.findByGroupId(groupId).stream()
                    .map(gm -> gm.getUser().getId())
                    .toList();
        }

        LinkedHashSet<Long> uniqueParticipantIds = new LinkedHashSet<>(participantUserIds);
        if (uniqueParticipantIds.size() != participantUserIds.size()) {
            throw new BadRequestException("Participants must be unique");
        }

        Long payer = req.payerUserId();
        requireMember(groupId, payer);
        for (Long uid : participantUserIds) requireMember(groupId, uid);

        LinkedHashSet<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payer);

        List<Long> splitUserIds = new ArrayList<>(participants);

        Map<Long, BigDecimal> calculatedShares;

        int splitModeCount = 0;
        List<String> providedModes = new ArrayList<>();
        if (req.getExactAmounts() != null && !req.getExactAmounts().isEmpty()) {
            splitModeCount++;
            providedModes.add("exactAmounts");
        }
        if (req.getPercentages() != null && !req.getPercentages().isEmpty()) {
            splitModeCount++;
            providedModes.add("percentages");
        }
        if (req.getShares() != null && !req.getShares().isEmpty()) {
            splitModeCount++;
            providedModes.add("shares");
        }

        if (splitModeCount > 1) {
            throw new BadRequestException("Only one split mode can be provided. Found: " + String.join(", ", providedModes));
        }

        if (req.getExactAmounts() != null && !req.getExactAmounts().isEmpty()) {
            var exactAmounts = req.getExactAmounts();
            if (exactAmounts.size() != participantUserIds.size()) {
                throw new BadRequestException("exactAmounts length must match participantUserIds length");
            }
            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                requestedShares.put(participantUserIds.get(i), normalizeAmount(exactAmounts.get(i)));
            }
            if (!requestedShares.containsKey(payer)) {
                requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal requestedTotal = requestedShares.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal tolerance = new BigDecimal("0.01");
            if (requestedTotal.subtract(totalAmount).abs().compareTo(tolerance) > 0) {
                throw new BadRequestException("Exact amounts must sum to total amount within $0.01 tolerance");
            }

            calculatedShares = rebalanceRoundedShares(requestedShares, totalAmount);

        } else if (req.getPercentages() != null && !req.getPercentages().isEmpty()) {
            var percentages = req.getPercentages();
            if (percentages.size() != participantUserIds.size()) {
                throw new BadRequestException("percentages length must match participantUserIds length");
            }
            BigDecimal percentageTotal = percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tolerance = new BigDecimal("0.01");
            if (percentageTotal.subtract(new BigDecimal("100")).abs().compareTo(tolerance) > 0) {
                throw new BadRequestException("Percentages must sum to 100% within 0.01 tolerance");
            }

            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal share = totalAmount.multiply(percentages.get(i))
                        .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
                requestedShares.put(participantUserIds.get(i), share.setScale(2, RoundingMode.DOWN));
            }

            if (!requestedShares.containsKey(payer)) {
                requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }

            calculatedShares = rebalanceRoundedShares(requestedShares, totalAmount);

        } else if (req.getShares() != null && !req.getShares().isEmpty()) {
            var shareWeights = req.getShares();
            if (shareWeights.size() != participantUserIds.size()) {
                throw new BadRequestException("shares length must match participantUserIds length");
            }
            int totalWeight = shareWeights.stream().mapToInt(Integer::intValue).sum();
            if (totalWeight <= 0) throw new BadRequestException("Sum of shares must be positive");

            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal fraction = new BigDecimal(shareWeights.get(i)).divide(new BigDecimal(totalWeight), 10, RoundingMode.HALF_UP);
                BigDecimal share = totalAmount.multiply(fraction).setScale(2, RoundingMode.DOWN);
                requestedShares.put(participantUserIds.get(i), share);
            }
            if (!requestedShares.containsKey(payer)) {
                requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            calculatedShares = rebalanceRoundedShares(requestedShares, totalAmount);

        } else {
            calculatedShares = splitEqually(totalAmount, splitUserIds);
        }

        Expense expense;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            expense = new Expense(groupId, payer, req.description().trim(), totalAmount, idempotencyKey);
        } else {
            expense = new Expense(groupId, payer, req.description().trim(), totalAmount);
        }
        expense.setVoided(false);
        expense = expenseRepo.save(expense);

        for (var shareEntry : calculatedShares.entrySet()) {
            participantRepo.save(new ExpenseParticipant(expense, shareEntry.getKey(), normalizeAmount(shareEntry.getValue())));
        }

        getOrCreateLedgerEntry(groupId, payer).add(totalAmount);
        for (var shareEntry : calculatedShares.entrySet()) {
            getOrCreateLedgerEntry(groupId, shareEntry.getKey()).add(shareEntry.getValue().negate());
        }

        String createdPayload = String.format("{\"expenseId\":%d,\"amount\":\"%s\"}", expense.getId(), expense.getAmount());
        eventRepo.save(new ExpenseEvent(groupId, expense.getId(), "ExpenseCreated", createdPayload));

        return toExpenseResponse(expense, calculatedShares);
    }

    private Map<Long, BigDecimal> rebalanceRoundedShares(Map<Long, BigDecimal> roundedShares, BigDecimal totalAmount) {
        List<Map.Entry<Long, BigDecimal>> entries = new ArrayList<>(roundedShares.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        Map<Long, BigDecimal> normalizedShares = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : entries) {
            normalizedShares.put(entry.getKey(), normalizeAmount(entry.getValue()));
        }

        BigDecimal allocatedTotal = normalizedShares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainder = totalAmount.setScale(2, RoundingMode.HALF_UP).subtract(allocatedTotal).setScale(2, RoundingMode.HALF_UP);

        int remainingCents = remainder.movePointRight(2).intValueExact();

        Map<Long, BigDecimal> rebalancedShares = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : entries) {
            rebalancedShares.put(entry.getKey(), normalizedShares.get(entry.getKey()));
        }

        int i = 0;
        int n = entries.size();
        while (remainingCents > 0) {
            Long id = entries.get(i % n).getKey();
            rebalancedShares.put(id, rebalancedShares.get(id).add(new BigDecimal("0.01")));
            i++;
            remainingCents--;
        }
        while (remainingCents < 0) {
            Long id = entries.get(i % n).getKey();
            rebalancedShares.put(id, rebalancedShares.get(id).subtract(new BigDecimal("0.01")));
            i++;
            remainingCents++;
        }

        rebalancedShares.replaceAll((k, v) -> normalizeAmount(v));

        return rebalancedShares;
    }

    @Transactional
    public LedgerResponse getLedger(Long groupId, Long actorUserId) {
        requireActorMember(groupId, actorUserId);
        var entries = ledgerRepo.findByGroupIdOrderByUserIdAsc(groupId).stream()
                .map(e -> new LedgerResponse.Entry(e.getUserId(), e.getNetBalance()))
                .toList();
        return new LedgerResponse(entries);
    }

    @Transactional
    public PaginatedResponse<ExpenseResponse> listExpenses(Long groupId, Long actorUserId, int page, int size, String sort, Instant fromDate, Instant toDate) {
        requireActorMember(groupId, actorUserId);
        Sort sortBy = SortUtils.parseSort(sort, "createdAt,desc");
        PageRequest pageRequest = PageRequest.of(page, size, sortBy);

        Page<com.fairshare.fairshare.expenses.model.Expense> expensesPage;
        if (fromDate != null && toDate != null) {
            expensesPage = expenseRepo.findByGroupIdAndVoidedFalseAndCreatedAtBetween(groupId, fromDate, toDate, pageRequest);
        } else {
            expensesPage = expenseRepo.findByGroupIdAndVoidedFalse(groupId, pageRequest);
        }

        List<Expense> pageExpenses = expensesPage.getContent();
        List<Long> expenseIds = pageExpenses.stream().map(Expense::getId).toList();
        Map<Long, Map<Long, BigDecimal>> sharesByExpenseId = new LinkedHashMap<>();
        if (!expenseIds.isEmpty()) {
            for (ExpenseParticipant p : participantRepo.findByExpenseIdInOrderByExpenseIdAscUserIdAsc(expenseIds)) {
                Long expenseId = p.getExpense().getId();
                sharesByExpenseId.computeIfAbsent(expenseId, ignored -> new LinkedHashMap<>())
                        .put(p.getUserId(), p.getShareAmount());
            }
        }

        List<ExpenseResponse> expenseResponses = pageExpenses.stream()
                .map(ex -> toExpenseResponse(ex, sharesByExpenseId.getOrDefault(ex.getId(), Map.of())))
                .toList();

        return new PaginatedResponse<>(
                expenseResponses,
                expensesPage.getTotalElements(),
                expensesPage.getTotalPages(),
                expensesPage.getNumber(),
                expensesPage.getSize()
        );
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
    public ConfirmSettlementsResponse confirmSettlements(Long groupId, Long actorUserId, ConfirmSettlementsRequest req, String confirmationIdHeader) {
        requireActorMember(groupId, actorUserId);
        if (req == null || req.getTransfers() == null || req.getTransfers().isEmpty()) {
            return new ConfirmSettlementsResponse(null, 0);
        }

        String confirmationId;
        if (confirmationIdHeader != null && !confirmationIdHeader.isBlank()) {
            confirmationId = confirmationIdHeader;
        } else if (req.getConfirmationId() != null && !req.getConfirmationId().isBlank()) {
            confirmationId = req.getConfirmationId();
        } else {
            confirmationId = UUID.randomUUID().toString();
        }

        var existing = confirmedTransferRepo.findByGroupIdAndConfirmationId(groupId, confirmationId);
        if (!existing.isEmpty()) {
            int appliedCount = confirmedTransferRepo.countByGroupIdAndConfirmationId(groupId, confirmationId);
            return new ConfirmSettlementsResponse(confirmationId, appliedCount);
        }

        int appliedCount = 0;
        for (var t : req.getTransfers()) {
            if (t.getAmount() == null || t.getAmount().signum() <= 0) {
                throw new BadRequestException("Transfer amount must be positive");
            }
            BigDecimal amt = normalizeAmount(t.getAmount());
            if (amt.signum() <= 0) throw new BadRequestException("Transfer amount must be positive and non-zero");

            Long from = t.getFromUserId();
            Long to = t.getToUserId();
            requireMember(groupId, from);
            requireMember(groupId, to);

            LedgerEntry fromEntry = getOrCreateLedgerEntry(groupId, from);
            LedgerEntry toEntry = getOrCreateLedgerEntry(groupId, to);

            fromEntry.add(amt);
            toEntry.add(amt.negate());

            ledgerRepo.save(fromEntry);
            ledgerRepo.save(toEntry);

            ConfirmedTransfer ct = new ConfirmedTransfer(groupId, from, to, amt, confirmationId);
            confirmedTransferRepo.save(ct);
            appliedCount++;
        }
        return new ConfirmSettlementsResponse(confirmationId, appliedCount);
    }

    @Transactional
    public BigDecimal amountOwedHistorical(Long groupId, Long actorUserId, Long fromUserId, Long toUserId) {
        requireActorMember(groupId, actorUserId);
        requireMember(groupId, fromUserId);
        requireMember(groupId, toUserId);

        BigDecimal obligations = participantRepo.sumShareByGroupAndPayerAndUser(groupId, toUserId, fromUserId);

        BigDecimal payments = confirmedTransferRepo.sumConfirmedAmount(groupId, fromUserId, toUserId);

        return obligations.subtract(payments).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireMember(Long groupId, Long userId) {
        if (!groupMemberRepo.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BadRequestException("User " + userId + " is not a member of group " + groupId);
        }
    }

    private void requireActorMember(Long groupId, Long actorUserId) {
        if (actorUserId == null) return;
        if (!groupMemberRepo.existsByGroupIdAndUserId(groupId, actorUserId)) {
            throw new ForbiddenException("User " + actorUserId + " is not a member of group " + groupId);
        }
    }

    private LedgerEntry getOrCreateLedgerEntry(Long groupId, Long userId) {
        return ledgerRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseGet(() -> ledgerRepo.save(new LedgerEntry(groupId, userId)));
    }

    private Map<Long, BigDecimal> splitEqually(BigDecimal amount, List<Long> userIds) {
        int participantCount = userIds.size();

        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(participantCount), 2, RoundingMode.DOWN);
        BigDecimal allocatedTotal = baseShare.multiply(BigDecimal.valueOf(participantCount));
        BigDecimal remainder = amount.subtract(allocatedTotal);

        Map<Long, BigDecimal> equalShares = new LinkedHashMap<>();
        for (Long id : userIds) equalShares.put(id, baseShare);

        int cents = remainder.movePointRight(2).intValueExact();
        for (int i = 0; i < cents; i++) {
            Long id = userIds.get(i % participantCount);
            equalShares.put(id, equalShares.get(id).add(new BigDecimal("0.01")));
        }
        equalShares.replaceAll((k, v) -> normalizeAmount(v));
        return equalShares;
    }

    @Transactional
    public SettlementResponse getSettlements(Long groupId, Long actorUserId) {
        requireActorMember(groupId, actorUserId);
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
    public ExpenseResponse updateExpense(Long groupId, Long actorUserId, Long expenseId, CreateExpenseRequest req) {
        requireActorMember(groupId, actorUserId);
        Expense ex = expenseRepo.findById(expenseId).orElseThrow(() -> new NotFoundException("Expense not found"));
        em.lock(ex, LockModeType.PESSIMISTIC_WRITE);
        if (!ex.getGroupId().equals(groupId)) throw new BadRequestException("Expense does not belong to group");
        if (ex.isVoided()) throw new BadRequestException("Expense is voided");

        Map<Long, BigDecimal> oldShares = new LinkedHashMap<>();
        for (ExpenseParticipant p : participantRepo.findByExpense_Id(expenseId))
            oldShares.put(p.getUserId(), p.getShareAmount());
        BigDecimal oldTotal = ex.getAmount();

        List<Long> participantUserIds = req.participantUserIds();
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            participantUserIds = new ArrayList<>(oldShares.keySet());
        }

        LinkedHashSet<Long> uniqueParticipantIds = new LinkedHashSet<>(participantUserIds);
        if (uniqueParticipantIds.size() != participantUserIds.size()) throw new BadRequestException("Participants must be unique");
        Long payer = req.payerUserId();
        requireMember(groupId, payer);
        for (Long uid : participantUserIds) requireMember(groupId, uid);
        LinkedHashSet<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payer);
        List<Long> splitUserIds = new ArrayList<>(participants);

        Map<Long, BigDecimal> newShares;
        BigDecimal totalAmount = normalizeAmount(req.amount());

        int splitModeCount = 0;
        List<String> providedModes = new ArrayList<>();
        if (req.getExactAmounts() != null && !req.getExactAmounts().isEmpty()) {
            splitModeCount++;
            providedModes.add("exactAmounts");
        }
        if (req.getPercentages() != null && !req.getPercentages().isEmpty()) {
            splitModeCount++;
            providedModes.add("percentages");
        }
        if (req.getShares() != null && !req.getShares().isEmpty()) {
            splitModeCount++;
            providedModes.add("shares");
        }

        if (splitModeCount > 1) {
            throw new BadRequestException("Only one split mode can be provided. Found: " + String.join(", ", providedModes));
        }

        if (req.getExactAmounts() != null && !req.getExactAmounts().isEmpty()) {
            var exactAmounts = req.getExactAmounts();
            if (exactAmounts.size() != participantUserIds.size())
                throw new BadRequestException("exactAmounts length must match participantUserIds length");
            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++)
                requestedShares.put(participantUserIds.get(i), normalizeAmount(exactAmounts.get(i)));
            if (!requestedShares.containsKey(payer)) requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = rebalanceRoundedShares(requestedShares, totalAmount);
        } else if (req.getPercentages() != null && !req.getPercentages().isEmpty()) {
            var percentages = req.getPercentages();
            if (percentages.size() != participantUserIds.size())
                throw new BadRequestException("percentages length must match participantUserIds length");
            BigDecimal percentageTotal = percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            if (percentageTotal.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0)
                throw new BadRequestException("Percentages must sum to 100% within 0.01 tolerance");
            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++)
                requestedShares.put(
                        participantUserIds.get(i),
                        totalAmount.multiply(percentages.get(i))
                                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                                .setScale(2, RoundingMode.DOWN)
                );
            if (!requestedShares.containsKey(payer)) requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = rebalanceRoundedShares(requestedShares, totalAmount);
        } else if (req.getShares() != null && !req.getShares().isEmpty()) {
            var shareWeights = req.getShares();
            if (shareWeights.size() != participantUserIds.size())
                throw new BadRequestException("shares length must match participantUserIds length");
            int totalWeight = shareWeights.stream().mapToInt(Integer::intValue).sum();
            if (totalWeight <= 0) throw new BadRequestException("Sum of shares must be positive");
            Map<Long, BigDecimal> requestedShares = new LinkedHashMap<>();
            for (int i = 0; i < participantUserIds.size(); i++) {
                BigDecimal fraction = new BigDecimal(shareWeights.get(i)).divide(new BigDecimal(totalWeight), 10, RoundingMode.HALF_UP);
                requestedShares.put(participantUserIds.get(i), totalAmount.multiply(fraction).setScale(2, RoundingMode.DOWN));
            }
            if (!requestedShares.containsKey(payer)) requestedShares.put(payer, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            newShares = rebalanceRoundedShares(requestedShares, totalAmount);
        } else {
            newShares = splitEqually(totalAmount, splitUserIds);
        }

        BigDecimal payerDelta = totalAmount.subtract(oldTotal).setScale(2, RoundingMode.HALF_UP);
        LedgerEntry payerEntry = getOrCreateLedgerEntry(groupId, payer);
        payerEntry.add(payerDelta);
        ledgerRepo.save(payerEntry);

        List<ExpenseParticipant> existingEntities = participantRepo.findByExpense_Id(expenseId);
        Map<Long, ExpenseParticipant> existingByUser = existingEntities.stream()
                .collect(Collectors.toMap(ExpenseParticipant::getUserId, ep -> ep, (a, b) -> a, LinkedHashMap::new));

        for (Map.Entry<Long, BigDecimal> shareEntry : newShares.entrySet()) {
            Long uid = shareEntry.getKey();
            BigDecimal newShare = normalizeAmount(shareEntry.getValue());

            BigDecimal oldShare = existingByUser.containsKey(uid) ? existingByUser.get(uid).getShareAmount() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal participantDelta = oldShare.subtract(newShare).setScale(2, RoundingMode.HALF_UP);
            LedgerEntry ledgerEntry = getOrCreateLedgerEntry(groupId, uid);
            ledgerEntry.add(participantDelta);
            ledgerRepo.save(ledgerEntry);

            if (existingByUser.containsKey(uid)) {
                ExpenseParticipant existingParticipant = existingByUser.get(uid);
                if (existingParticipant.getShareAmount().compareTo(newShare) != 0) {
                    participantRepo.deleteByExpense_IdAndUserId(expenseId, uid);
                    participantRepo.flush();
                    participantRepo.save(new ExpenseParticipant(ex, uid, newShare));
                }
                existingByUser.remove(uid);
            } else {
                participantRepo.deleteByExpense_IdAndUserId(expenseId, uid);
                participantRepo.flush();
                participantRepo.save(new ExpenseParticipant(ex, uid, newShare));
            }
        }

        for (ExpenseParticipant removed : existingByUser.values()) {
            Long uid = removed.getUserId();
            BigDecimal oldShare = removed.getShareAmount();
            LedgerEntry ledgerEntry = getOrCreateLedgerEntry(groupId, uid);
            ledgerEntry.add(oldShare);
            ledgerRepo.save(ledgerEntry);
            participantRepo.delete(removed);
        }

        ex.setAmount(totalAmount);
        ex.setDescription(req.description().trim());
        expenseRepo.save(ex);

        String payload = String.format("{\"before\":{\"amount\":\"%s\"},\"after\":{\"amount\":\"%s\"}}", oldTotal, totalAmount);
        eventRepo.save(new ExpenseEvent(groupId, expenseId, "ExpenseUpdated", payload));

        return toExpenseResponse(ex, newShares);
    }

    @Transactional
    public void voidExpense(Long groupId, Long actorUserId, Long expenseId) {
        requireActorMember(groupId, actorUserId);
        Expense ex = expenseRepo.findById(expenseId).orElseThrow(() -> new NotFoundException("Expense not found"));
        if (!ex.getGroupId().equals(groupId)) throw new BadRequestException("Expense does not belong to group");
        if (ex.isVoided()) return;

        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        for (ExpenseParticipant p : participantRepo.findByExpense_Id(expenseId))
            shares.put(p.getUserId(), p.getShareAmount());

        BigDecimal total = ex.getAmount();
        LedgerEntry payerEntry = getOrCreateLedgerEntry(groupId, ex.getPayerUserId());
        payerEntry.add(total.negate());
        ledgerRepo.save(payerEntry);

        for (var shareEntry : shares.entrySet()) {
            LedgerEntry ledgerEntry = getOrCreateLedgerEntry(groupId, shareEntry.getKey());
            ledgerEntry.add(shareEntry.getValue());
            ledgerRepo.save(ledgerEntry);
        }

        ex.setVoided(true);
        expenseRepo.save(ex);

        String payload = String.format("{\"expenseId\":%d,\"amount\":\"%s\"}", expenseId, total);
        eventRepo.save(new ExpenseEvent(groupId, expenseId, "ExpenseVoided", payload));
    }

    @Transactional
    public PaginatedResponse<EventResponse> listEvents(Long groupId, Long actorUserId, int page, int size, String sort, Instant fromDate, Instant toDate) {
        requireActorMember(groupId, actorUserId);
        Sort sortBy = SortUtils.parseSort(sort, "createdAt,desc");
        PageRequest pageRequest = PageRequest.of(page, size, sortBy);

        Page<ExpenseEvent> eventPage;
        if (fromDate != null && toDate != null) {
            eventPage = eventRepo.findByGroupIdAndCreatedAtBetween(groupId, fromDate, toDate, pageRequest);
        } else {
            eventPage = eventRepo.findByGroupId(groupId, pageRequest);
        }

        List<EventResponse> eventResponses = eventPage.getContent().stream()
                .map(e -> new EventResponse(e.getId(), e.getGroupId(), e.getExpenseId(), e.getEventType(), e.getPayload(), e.getCreatedAt()))
                .toList();

        return new PaginatedResponse<>(
                eventResponses,
                eventPage.getTotalElements(),
                eventPage.getTotalPages(),
                eventPage.getNumber(),
                eventPage.getSize()
        );
    }

    @Transactional
    public PaginatedResponse<ConfirmedTransferResponse> listConfirmedTransfers(Long groupId, Long actorUserId, String confirmationId, int page, int size, String sort, Instant fromDate, Instant toDate) {
        requireActorMember(groupId, actorUserId);
        Sort sortBy = SortUtils.parseSort(sort, "createdAt,desc");
        PageRequest pageRequest = PageRequest.of(page, size, sortBy);

        Page<ConfirmedTransfer> transferPage;
        if (confirmationId != null && !confirmationId.isBlank()) {
            transferPage = confirmedTransferRepo.findByGroupIdAndConfirmationId(groupId, confirmationId, pageRequest);
        } else if (fromDate != null && toDate != null) {
            transferPage = confirmedTransferRepo.findByGroupIdAndCreatedAtBetween(groupId, fromDate, toDate, pageRequest);
        } else {
            transferPage = confirmedTransferRepo.findByGroupId(groupId, pageRequest);
        }

        List<ConfirmedTransferResponse> transferResponses = transferPage.getContent().stream()
                .map(ct -> new ConfirmedTransferResponse(ct.getId(), ct.getGroupId(), ct.getFromUserId(), ct.getToUserId(), ct.getAmount(), ct.getConfirmationId(), ct.getCreatedAt()))
                .toList();

        return new PaginatedResponse<>(
                transferResponses,
                transferPage.getTotalElements(),
                transferPage.getTotalPages(),
                transferPage.getNumber(),
                transferPage.getSize()
        );
    }

    @Transactional
    public LedgerExplanationResponse getLedgerExplanation(Long groupId, Long actorUserId) {
        requireActorMember(groupId, actorUserId);
        List<GroupMember> members = groupMemberRepo.findByGroupId(groupId);
        List<LedgerExplanationResponse.UserLedgerExplanation> explanations = new ArrayList<>();

        for (GroupMember member : members) {
            Long userId = member.getUser().getId();
            List<LedgerExplanationResponse.Contribution> contributions = new ArrayList<>();
            BigDecimal netBalance = BigDecimal.ZERO;

            // Expenses paid by the user
            List<Expense> paidExpenses = expenseRepo.findByGroupIdAndPayerUserId(groupId, userId);
            for (Expense expense : paidExpenses) {
                contributions.add(new LedgerExplanationResponse.Contribution(
                        "EXPENSE_PAID",
                        expense.getAmount(),
                        expense.getDescription(),
                        expense.getCreatedAt(),
                        expense.getId()
                ));
                netBalance = netBalance.add(expense.getAmount());
            }

            // User's share in all expenses
            List<ExpenseParticipant> participations = participantRepo.findByUserIdAndGroupId(userId, groupId);
            for (ExpenseParticipant participation : participations) {
                contributions.add(new LedgerExplanationResponse.Contribution(
                        "EXPENSE_SHARE",
                        participation.getShareAmount().negate(),
                        participation.getExpense().getDescription(),
                        participation.getExpense().getCreatedAt(),
                        participation.getExpense().getId()
                ));
                netBalance = netBalance.subtract(participation.getShareAmount());
            }

            // Transfers sent by the user
            List<ConfirmedTransfer> sentTransfers = confirmedTransferRepo.findByGroupIdAndFromUserId(groupId, userId);
            for (ConfirmedTransfer transfer : sentTransfers) {
                contributions.add(new LedgerExplanationResponse.Contribution(
                        "TRANSFER_SENT",
                        transfer.getAmount(),
                        "Transfer to user " + transfer.getToUserId(),
                        transfer.getCreatedAt(),
                        transfer.getId()
                ));
                netBalance = netBalance.add(transfer.getAmount());
            }

            // Transfers received by the user
            List<ConfirmedTransfer> receivedTransfers = confirmedTransferRepo.findByGroupIdAndToUserId(groupId, userId);
            for (ConfirmedTransfer transfer : receivedTransfers) {
                contributions.add(new LedgerExplanationResponse.Contribution(
                        "TRANSFER_RECEIVED",
                        transfer.getAmount().negate(),
                        "Transfer from user " + transfer.getFromUserId(),
                        transfer.getCreatedAt(),
                        transfer.getId()
                ));
                netBalance = netBalance.subtract(transfer.getAmount());
            }

            contributions.sort(Comparator.comparing(LedgerExplanationResponse.Contribution::timestamp).reversed());

            explanations.add(new LedgerExplanationResponse.UserLedgerExplanation(
                    userId,
                    netBalance,
                    contributions
            ));
        }

        return new LedgerExplanationResponse(explanations);
    }
}
