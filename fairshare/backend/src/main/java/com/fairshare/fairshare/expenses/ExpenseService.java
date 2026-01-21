package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.api.ExpenseResponse;
import com.fairshare.fairshare.expenses.api.LedgerResponse;
import com.fairshare.fairshare.groups.GroupMemberRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

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
        // Validate membership (payer + participants must be in group)
        requireMember(groupId, payerUserId);
        for (Long uid : participantUserIds) requireMember(groupId, uid);

        // Ensure payer included
        Set<Long> participants = new LinkedHashSet<>(participantUserIds);
        participants.add(payerUserId);

        // Split with cents-correct rounding
        List<Long> ids = new ArrayList<>(participants);
        Map<Long, BigDecimal> shares = equalSplit(amount, ids);

        Expense expense = expenseRepo.save(new Expense(groupId, payerUserId, description.trim(), amount));
        for (var e : shares.entrySet()) {
            participantRepo.save(new ExpenseParticipant(expense.getId(), e.getKey(), e.getValue()));
        }

        // Ledger update: payer gets +amount; everyone owes their share (negative)
        ledger(groupId, payerUserId).add(amount);
        for (var e : shares.entrySet()) {
            ledger(groupId, e.getKey()).add(e.getValue().negate());
        }

        List<ExpenseResponse.Split> splits = shares.entrySet().stream()
                .map(x -> new ExpenseResponse.Split(x.getKey(), x.getValue()))
                .toList();

        return new ExpenseResponse(expense.getId(), expense.getDescription(), expense.getAmount(), expense.getPayerUserId(), splits);
    }

    @Transactional
    public LedgerResponse getLedger(Long groupId) {
        var entries = ledgerRepo.findByGroupId(groupId).stream()
                .map(e -> new LedgerResponse.Entry(e.getUserId(), e.getNetBalance()))
                .toList();
        return new LedgerResponse(entries);
    }

    private void requireMember(Long groupId, Long userId) {
        // uses method name derived from your GroupMemberRepository:
        // boolean existsByGroupIdAndUserId(Long groupId, Long userId)
        if (!groupMemberRepo.existsByGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException("User " + userId + " is not a member of group " + groupId);
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

        int cents = remainder.movePointRight(2).intValueExact(); // safe because scale=2
        for (int i = 0; i < cents; i++) {
            Long id = userIds.get(i % n);
            out.put(id, out.get(id).add(new BigDecimal("0.01")));
        }
        return out;
    }
}
