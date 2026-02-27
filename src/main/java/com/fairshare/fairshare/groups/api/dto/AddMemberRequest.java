package com.fairshare.fairshare.groups.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record AddMemberRequest(
        @Size(max = 50) String name,
        @Email @Size(max = 320) String email,
        @Positive Long userId
) {
    @SuppressWarnings("unused")
    @AssertTrue(message = "Either userId or email must be provided")
    public boolean hasEmailOrUserId() {
        return (email != null && !email.trim().isBlank()) || userId != null;
    }
}
