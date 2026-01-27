package com.fairshare.fairshare.expenses;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<Expense> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);

    List<Expense> findByGroupIdAndVoidedFalseOrderByCreatedAtDesc(Long groupId);

    List<Expense> findByGroupIdAndPayerUserId(Long groupId, Long payerUserId);

    Page<Expense> findByGroupIdAndVoidedFalse(Long groupId, Pageable pageable);

    Page<Expense> findByGroupIdAndVoidedFalseAndCreatedAtBetween(Long groupId, Instant fromDate, Instant toDate, Pageable pageable);
}
