package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record LedgerResponse(List<Entry> entries) {
    public record Entry(
            Long userId,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            @Schema(type = "string", example = "-15.50")
            BigDecimal netBalance
    ) {
    }
}
