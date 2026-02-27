package com.fairshare.fairshare.users.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 320) String email
) {
}
