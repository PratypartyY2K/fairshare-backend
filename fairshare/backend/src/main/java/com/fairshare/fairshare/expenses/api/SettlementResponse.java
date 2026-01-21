package com.fairshare.fairshare.expenses.api;

import java.math.BigDecimal;
import java.util.List;

public record SettlementResponse(List<Transfer> transfers) {
    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
    }
}
