package com.fairshare.fairshare.groups.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupUpdateRequest {
    @NotBlank
    @Size(max = 80)
    private String name;

    public GroupUpdateRequest() {
    }

    public GroupUpdateRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
