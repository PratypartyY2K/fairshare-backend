package com.fairshare.fairshare.expenses.api;

import java.math.BigDecimal;
import java.util.List;

public record LedgerResponse(List<Entry> entries) {
    public record Entry(Long userId, BigDecimal netBalance) {
    }
}
