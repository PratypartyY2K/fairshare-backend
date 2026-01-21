package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.expenses.api.ExpenseResponse;
import com.fairshare.fairshare.expenses.api.LedgerResponse;
import com.fairshare.fairshare.groups.GroupMemberRepository;
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

    public ExpenseService(
            ExpenseRepository expenseRepo,
            ExpenseParticipantRepository participantRepo,
            LedgerEntryRepository ledgerRepo,
            GroupMemberRepository groupMemberRepo
    ) {
        this.expenseRepo = expenseRepo;
        this.participantRepo = participantRepo;
        this.ledgerRepo = ledgerRepo;
        this.groupMemberRepo = groupMemberRepo;
    }

    @Transactional
    public ExpenseResponse createExpense(
            Long groupId,
            String description,
            BigDecimal amount,
            Long payerUserId,
            List<Long> participantUserIds
    ) {
        requireMember(groupId, payerUserId);
        for (Long uid : participantUserIds) requireMember(groupId, uid);

        LinkedHashSet<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payerUserId);

        if (participants.isEmpty()) {
            throw new BadRequestException("At least one participant is required");
        }

        List<Long> ids = new ArrayList<>(participants);
        Map<Long, BigDecimal> shares = equalSplit(amount, ids);

        Expense expense = expenseRepo.save(new Expense(groupId, payerUserId, description.trim(), amount));

        for (var e : shares.entrySet()) {
            participantRepo.save(new ExpenseParticipant(expense.getId(), e.getKey(), e.getValue()));
        }

        // Ledger:
        // payer paid the full amount (+amount)
        ledger(groupId, payerUserId).add(amount);
        // everyone owes their share (-share)
        for (var e : shares.entrySet()) {
            ledger(groupId, e.getKey()).add(e.getValue().negate());
        }

        return toExpenseResponse(expense, shares);
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
