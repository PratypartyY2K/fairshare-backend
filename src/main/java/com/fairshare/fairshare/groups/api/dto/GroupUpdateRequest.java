package com.fairshare.fairshare.groups.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GroupUpdateRequest {
    @NotBlank
    @Size(max = 80)
    private String name;

    public GroupUpdateRequest(String name) {
        this.name = name;
    }

}
