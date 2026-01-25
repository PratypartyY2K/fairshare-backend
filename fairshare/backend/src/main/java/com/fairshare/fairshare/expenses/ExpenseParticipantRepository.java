package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    List<ExpenseParticipant> findByExpenseId(Long expenseId);

    void deleteByExpenseIdAndUserId(Long expenseId, Long userId);

    @Query("select coalesce(sum(p.shareAmount), 0) from ExpenseParticipant p join Expense e on p.expenseId = e.id where e.groupId = :groupId and e.payerUserId = :payer and p.userId = :user")
    BigDecimal sumShareByGroupAndPayerAndUser(@Param("groupId") Long groupId, @Param("payer") Long payerUserId, @Param("user") Long participantUserId);
}
