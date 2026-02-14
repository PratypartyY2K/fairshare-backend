package com.fairshare.fairshare.expenses;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "ledger_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"})
)
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // net_balance = paid - owed
    @Column(name = "net_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal netBalance = BigDecimal.ZERO;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
        this.netBalance = BigDecimal.ZERO;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public void add(BigDecimal delta) {
        this.netBalance = this.netBalance.add(delta);
    }
}
