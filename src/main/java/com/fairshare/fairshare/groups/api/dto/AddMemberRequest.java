package com.fairshare.fairshare.groups.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record AddMemberRequest(
        @Size(max = 50) String name,
        @Positive Long userId
) {
    public AddMemberRequest(String name) {
        this(name, null);
    }

    @SuppressWarnings("unused")
    @AssertTrue(message = "Either name or userId must be provided")
    public boolean hasNameOrUserId() {
        return (name != null && !name.trim().isBlank()) || userId != null;
    }
}
