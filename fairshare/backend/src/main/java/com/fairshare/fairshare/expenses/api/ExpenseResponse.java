package com.fairshare.fairshare.expenses.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseResponse(
        Long expenseId,
        Long groupId,
        String description,
        BigDecimal amount,
        Long payerUserId,
        Instant createdAt,
        List<Split> splits
) {
    public record Split(Long userId, BigDecimal shareAmount) {
    }
}
