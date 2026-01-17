package com.fairshare.fairshare.groups.api;

import java.util.List;

public record GroupResponse(Long id, String name, List<MemberDto> members) {
    public record MemberDto(Long id, String name) {}
}
