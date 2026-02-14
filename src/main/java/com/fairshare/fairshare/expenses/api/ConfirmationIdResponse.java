package com.fairshare.fairshare.expenses.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConfirmationIdResponse(
        @Schema(description = "A unique confirmation id (UUID) for idempotent settlement confirmation", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String confirmationId
) {
}
