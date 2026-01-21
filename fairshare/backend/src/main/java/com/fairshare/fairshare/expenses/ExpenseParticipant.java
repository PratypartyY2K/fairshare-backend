package com.fairshare.fairshare.expenses;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "expense_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"})
)
public class ExpenseParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    protected ExpenseParticipant() {
    }

    public ExpenseParticipant(Long expenseId, Long userId, BigDecimal shareAmount) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }
}
