package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @SuppressWarnings("unused")
    List<Expense> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<Expense> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);

    @SuppressWarnings("unused")
    List<Expense> findByGroupIdAndVoidedFalseOrderByCreatedAtDesc(Long groupId);

    List<Expense> findByGroupIdAndPayerUserId(Long groupId, Long payerUserId);

    Page<Expense> findByGroupIdAndVoidedFalse(Long groupId, Pageable pageable);

    Page<Expense> findByGroupIdAndVoidedFalseAndCreatedAtBetween(Long groupId, Instant fromDate, Instant toDate, Pageable pageable);
}
