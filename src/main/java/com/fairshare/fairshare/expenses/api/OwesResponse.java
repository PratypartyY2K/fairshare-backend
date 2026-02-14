package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record OwesResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "5.00")
        BigDecimal amount
) {
}
