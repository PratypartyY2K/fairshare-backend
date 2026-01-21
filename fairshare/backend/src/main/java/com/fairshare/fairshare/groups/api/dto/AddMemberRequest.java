package com.fairshare.fairshare.groups.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddMemberRequest(
        @NotBlank @Size(max = 50) String userName
) {
}
