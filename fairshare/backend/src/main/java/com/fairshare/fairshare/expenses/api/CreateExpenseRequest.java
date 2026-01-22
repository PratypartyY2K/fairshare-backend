package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateExpenseRequest(
        @NotBlank String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull @JsonAlias({"paidByUserId", "payerUserId"}) Long payerUserId,
        // participants can be omitted in which case we default to all group members
        List<Long> participantUserIds
) {
}
