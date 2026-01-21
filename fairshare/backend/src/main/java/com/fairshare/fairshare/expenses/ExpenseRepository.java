package com.fairshare.fairshare.expenses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
