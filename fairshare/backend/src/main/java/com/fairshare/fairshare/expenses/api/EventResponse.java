package com.fairshare.fairshare.expenses.api;

import java.time.Instant;

public record EventResponse(Long eventId, Long groupId, Long expenseId, String eventType, String payload,
                            Instant createdAt) {
}
