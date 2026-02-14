package com.fairshare.fairshare.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard API error response structure.")
public record ApiError(
        @Schema(description = "Timestamp of when the error occurred.", example = "2023-10-27T10:00:00Z")
        Instant timestamp,
        @Schema(description = "HTTP status code.", example = "400")
        int status,
        @Schema(description = "HTTP status reason phrase.", example = "Bad Request")
        String error,
        @Schema(description = "A developer-friendly message about the error.", example = "Only one split mode can be provided.")
        String message,
        @Schema(description = "The API path where the error occurred.", example = "/groups/1/expenses")
        String path,
        @Schema(description = "List of specific error details, e.g., field validation errors.")
        List<String> details
) {
}
