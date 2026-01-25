package com.fairshare.fairshare.expenses.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmedTransferResponse(Long id, Long groupId, Long fromUserId, Long toUserId, BigDecimal amount,
                                        String confirmationId, Instant createdAt) {
}
