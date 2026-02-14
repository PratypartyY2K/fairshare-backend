package com.fairshare.fairshare.expenses.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddExpenseRequest(
        @NotBlank String description,
        @NotNull @Positive Double amount,
        @NotNull Long paidByUserId
) {
}
