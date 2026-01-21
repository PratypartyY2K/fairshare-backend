package com.fairshare.fairshare.expenses.api;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseResponse(
        Long expenseId,
        String description,
        BigDecimal amount,
        Long payerUserId,
        List<Split> splits
) {
    public record Split(Long userId, BigDecimal shareAmount) {
    }
}
