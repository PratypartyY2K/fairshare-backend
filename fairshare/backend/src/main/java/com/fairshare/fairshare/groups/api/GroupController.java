package com.fairshare.fairshare.groups.api;

import com.fairshare.fairshare.groups.Group;
import com.fairshare.fairshare.groups.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable Long groupId, @Valid @RequestBody AddMemberRequest req) {
        service.addMember(groupId, req.userName());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable Long groupId) {
        return service.getGroup(groupId);
    }
}
