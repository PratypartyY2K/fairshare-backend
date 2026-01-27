package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LedgerExplanationResponse(
        @Schema(description = "List of ledger explanations for each user in the group.")
        List<UserLedgerExplanation> explanations
) {
    public record UserLedgerExplanation(
            @Schema(description = "The ID of the user.")
            Long userId,

            @JsonFormat(shape = JsonFormat.Shape.STRING)
            @Schema(type = "string", description = "The net balance of the user in the group.", example = "50.25")
            BigDecimal netBalance,

            @Schema(description = "A list of transactions contributing to the user's net balance.")
            List<Contribution> contributions
    ) {}

    public record Contribution(
            @Schema(description = "The type of contribution (e.g., EXPENSE_PAID, EXPENSE_SHARE, TRANSFER_SENT, TRANSFER_RECEIVED).")
            String type,

            @JsonFormat(shape = JsonFormat.Shape.STRING)
            @Schema(type = "string", description = "The amount of the contribution. Positive if it increases the balance, negative if it decreases.", example = "100.50")
            BigDecimal amount,

            @Schema(description = "A description of the contribution (e.g., expense description or transfer details).")
            String description,

            @Schema(description = "The timestamp of the contribution.")
            Instant timestamp,

            @Schema(description = "The ID of the underlying expense or transfer.")
            Long referenceId
    ) {}
}
