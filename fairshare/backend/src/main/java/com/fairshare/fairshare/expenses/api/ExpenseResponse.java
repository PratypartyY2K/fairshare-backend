package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseResponse(
        Long expenseId,
        Long groupId,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "30.75")
        BigDecimal amount,
        Long payerUserId,
        Instant createdAt,
        List<Split> splits
) {
    public record Split(
            Long userId,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            @Schema(type = "string", example = "10.25")
            BigDecimal shareAmount
    ) {
    }
}
