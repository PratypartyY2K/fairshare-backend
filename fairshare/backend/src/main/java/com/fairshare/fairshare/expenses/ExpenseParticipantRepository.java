package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    List<ExpenseParticipant> findByExpenseId(Long expenseId);

    void deleteByExpenseIdAndUserId(Long expenseId, Long userId);

    @Query("SELECT COALESCE(SUM(ep.shareAmount), 0) FROM ExpenseParticipant ep JOIN ep.expense e WHERE e.groupId = ?1 AND e.payerUserId = ?2 AND ep.userId = ?3")
    BigDecimal sumShareByGroupAndPayerAndUser(Long groupId, Long payerUserId, Long participantUserId);

    @Query("SELECT ep FROM ExpenseParticipant ep JOIN ep.expense e WHERE ep.userId = ?1 AND e.groupId = ?2")
    List<ExpenseParticipant> findByUserIdAndGroupId(Long userId, Long groupId);
}
