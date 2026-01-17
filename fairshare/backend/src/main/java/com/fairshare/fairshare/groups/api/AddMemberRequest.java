package com.fairshare.fairshare.groups.api;

import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(@NotBlank String userName) {}
