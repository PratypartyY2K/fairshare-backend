package com.fairshare.fairshare.expenses;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "payer_user_id", nullable = false)
    private Long payerUserId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Expense() {
    }

    public Expense(Long groupId, Long payerUserId, String description, java.math.BigDecimal amount) {
        this.groupId = groupId;
        this.payerUserId = payerUserId;
        this.description = description;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getPayerUserId() {
        return payerUserId;
    }

    public String getDescription() {
        return description;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
