package com.fairshare.fairshare.groups.api.dto;

import java.util.List;

public record GroupResponse(Long id, String name, List<MemberResponse> members, int memberCount) {
}
