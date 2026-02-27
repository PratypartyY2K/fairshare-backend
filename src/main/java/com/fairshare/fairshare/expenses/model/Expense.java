package com.fairshare.fairshare.expenses;

import jakarta.persistence.*;

import java.math.BigDecimal;
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
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "voided")
    private Boolean voided;

    protected Expense() {
    }

    public Expense(Long groupId, Long payerUserId, String description, BigDecimal amount) {
        this.groupId = groupId;
        this.payerUserId = payerUserId;
        this.description = description;
        this.amount = amount;
    }

    public Expense(Long groupId, Long payerUserId, String description, BigDecimal amount, String idempotencyKey) {
        this.groupId = groupId;
        this.payerUserId = payerUserId;
        this.description = description;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
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

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isVoided() {
        return Boolean.TRUE.equals(voided);
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
