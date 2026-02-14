package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmedTransferResponse(
        Long id,
        Long groupId,
        Long fromUserId,
        Long toUserId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "15.50")
        BigDecimal amount,
        String confirmationId,
        Instant createdAt
) {
}
