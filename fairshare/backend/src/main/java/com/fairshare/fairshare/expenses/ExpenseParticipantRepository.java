package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseParticipantRepository extends JpaRepository<ExpenseParticipant, Long> {
    List<ExpenseParticipant> findByExpenseId(Long expenseId);
}
