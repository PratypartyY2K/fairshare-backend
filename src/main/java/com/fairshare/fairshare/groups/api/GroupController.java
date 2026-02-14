package com.fairshare.fairshare.groups.api;

import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.Group;
import com.fairshare.fairshare.groups.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.api.dto.GroupUpdateRequest;
import com.fairshare.fairshare.groups.api.dto.CreateGroupRequest;
import com.fairshare.fairshare.groups.api.dto.AddMemberRequest;

import java.util.List;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest req) {
        Group g = service.createGroup(req.name());
        return service.getGroup(g.getId());
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public AddMemberResponse addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest req) {
        return service.addMember(groupId, req.name());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable Long groupId) {
        return service.getGroup(groupId);
    }

    @GetMapping
    public PaginatedResponse<GroupResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "name", required = false) String name
    ) {
        int effectiveSize = pageSize != null ? pageSize : size;
        return service.listGroups(page, effectiveSize, sort, name);
    }

    @PatchMapping("/{groupId}")
    public GroupResponse patchName(@PathVariable Long groupId, @RequestBody @Valid GroupUpdateRequest req) {
        return service.updateGroupName(groupId, req.getName());
    }

}
