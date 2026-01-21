package com.fairshare.fairshare.expenses.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateExpenseRequest(
        @NotBlank String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull Long payerUserId,
        @NotNull @Size(min = 1) List<Long> participantUserIds
) {
}
