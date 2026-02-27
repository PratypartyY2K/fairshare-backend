package com.fairshare.fairshare.expenses.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "confirmed_transfers")
public class ConfirmedTransfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "confirmation_id", length = 128)
    private String confirmationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConfirmedTransfer() {
        // Required by JPA
    }

    public ConfirmedTransfer(Long groupId, Long fromUserId, Long toUserId, BigDecimal amount, String confirmationId) {
        this.groupId = groupId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.confirmationId = confirmationId;
        this.createdAt = Instant.now();
    }

}
