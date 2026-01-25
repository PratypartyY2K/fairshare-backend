package com.fairshare.fairshare.expenses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<Expense> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);

    List<Expense> findByGroupIdAndVoidedFalseOrderByCreatedAtDesc(Long groupId);
}
