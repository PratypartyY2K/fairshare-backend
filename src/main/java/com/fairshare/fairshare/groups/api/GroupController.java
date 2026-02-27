package com.fairshare.fairshare.groups.api;

import com.fairshare.fairshare.auth.AuthContext;
import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.service.GroupService;
import com.fairshare.fairshare.groups.model.Group;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.api.dto.GroupUpdateRequest;
import com.fairshare.fairshare.groups.api.dto.CreateGroupRequest;
import com.fairshare.fairshare.groups.api.dto.AddMemberRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/groups")
@Tag(name = "Groups")
@SecurityRequirement(name = "user-id-header")
public class GroupController {

    private final GroupService service;
    private final AuthContext authContext;

    public GroupController(GroupService service, AuthContext authContext) {
        this.service = service;
        this.authContext = authContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest req, HttpServletRequest request) {
        Long actorUserId = authContext.getActorUserId(request);
        Group g = service.createGroup(req.name(), actorUserId);
        return service.getGroup(g.getId(), actorUserId);
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public AddMemberResponse addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest req, HttpServletRequest request) {
        Long actorUserId = authContext.getActorUserId(request);
        return service.addMember(groupId, actorUserId, req.name(), req.email(), req.userId());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable Long groupId, HttpServletRequest request) {
        return service.getGroup(groupId, authContext.getActorUserId(request));
    }

    @GetMapping
    public PaginatedResponse<GroupResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "name", required = false) String name,
            HttpServletRequest request
    ) {
        int effectiveSize = pageSize != null ? pageSize : size;
        return service.listGroups(authContext.getActorUserId(request), page, effectiveSize, sort, name);
    }

    @PatchMapping("/{groupId}")
    public GroupResponse patchName(@PathVariable Long groupId, @RequestBody @Valid GroupUpdateRequest req, HttpServletRequest request) {
        return service.updateGroupName(groupId, authContext.getActorUserId(request), req.getName());
    }

}
