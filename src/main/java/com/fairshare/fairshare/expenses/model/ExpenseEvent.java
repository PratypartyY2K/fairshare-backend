package com.fairshare.fairshare.expenses.model;

import jakarta.persistence.*;

import java.time.Instant;

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
    private Instant createdAt = Instant.now();

    protected ExpenseEvent() {
    }

    public ExpenseEvent(Long groupId, Long expenseId, String eventType, String payload) {
        this.groupId = groupId;
        this.expenseId = expenseId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
