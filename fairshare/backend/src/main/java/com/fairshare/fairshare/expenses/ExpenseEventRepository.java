package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ExpenseEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseEventRepository extends JpaRepository<ExpenseEvent, Long> {
    List<ExpenseEvent> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
