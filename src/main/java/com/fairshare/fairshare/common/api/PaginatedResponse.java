package com.fairshare.fairshare.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "A generic paginated response wrapper.")
public record PaginatedResponse<T>(
        @Schema(description = "The list of items for the current page.")
        List<T> items,
        @Schema(description = "The total number of items available across all pages.")
        long totalItems,
        @Schema(description = "The total number of pages available.")
        int totalPages,
        @Schema(description = "The current page number (0-indexed).")
        int currentPage,
        @Schema(description = "The number of items per page.")
        int pageSize
) {
}
