package com.fairshare.fairshare.expenses.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "expense_events")
public class ExpenseEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

    @SuppressWarnings("unused")
    protected ExpenseEvent() {
    }

    public ExpenseEvent(Long groupId, Long expenseId, String eventType, String payload) {
        this.groupId = groupId;
        this.expenseId = expenseId;
        this.eventType = eventType;
        this.payload = payload;
    }

}
