package com.fairshare.fairshare.expenses.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response after confirming settlements, including the confirmation ID and the number of transfers applied.")
public record ConfirmSettlementsResponse(
        @Schema(description = "The confirmation ID (UUID) used for the settlement. This is either client-provided or server-generated.", example = "a1b2c3d4-e5f6-7890-1234-567890abcdef")
        String confirmationId,

        @Schema(description = "The number of transfers successfully applied in this settlement.", example = "3")
        int appliedTransfersCount
) {
}
