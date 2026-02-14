package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ExpenseEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ExpenseEventRepository extends JpaRepository<ExpenseEvent, Long> {
    List<ExpenseEvent> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Page<ExpenseEvent> findByGroupId(Long groupId, Pageable pageable);

    Page<ExpenseEvent> findByGroupIdAndCreatedAtBetween(Long groupId, Instant fromDate, Instant toDate, Pageable pageable);
}
