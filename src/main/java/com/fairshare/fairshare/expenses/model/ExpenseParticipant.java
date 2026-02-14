package com.fairshare.fairshare.expenses.model;

import com.fairshare.fairshare.expenses.Expense;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    protected ExpenseParticipant() {
    }

    public ExpenseParticipant(Expense expense, Long userId, BigDecimal shareAmount) {
        this.expense = expense;
        this.userId = userId;
        this.shareAmount = shareAmount;
    }

    public Long getId() {
        return id;
    }

    public Expense getExpense() {
        return expense;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }
}
