package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record SettlementResponse(List<Transfer> transfers) {
    public record Transfer(
            Long fromUserId,
            Long toUserId,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            @Schema(type = "string", example = "12.50")
            BigDecimal amount
    ) {
    }
}
