package com.fairshare.fairshare.expenses.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "expenses")
public class Expense {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Getter
    @Column(name = "payer_user_id", nullable = false)
    private Long payerUserId;

    @Setter
    @Getter
    @Column(nullable = false)
    private String description;

    @Setter
    @Getter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Getter
    @Column(nullable = false, updatable = false)
    private final Instant createdAt = Instant.now();

    @Getter
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

    public boolean isVoided() {
        return Boolean.TRUE.equals(voided);
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }

}
