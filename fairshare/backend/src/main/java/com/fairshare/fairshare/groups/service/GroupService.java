package com.fairshare.fairshare.groups.service;

import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.Group;
import com.fairshare.fairshare.groups.GroupMember;
import com.fairshare.fairshare.groups.GroupMemberRepository;
import com.fairshare.fairshare.groups.GroupRepository;
import com.fairshare.fairshare.groups.api.AddMemberResponse;
import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.api.dto.MemberResponse;
import com.fairshare.fairshare.users.User;
import com.fairshare.fairshare.users.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupService {
    private final GroupRepository groupRepo;
    private final UserRepository userRepo;
    private final GroupMemberRepository memberRepo;

    public GroupService(GroupRepository groupRepo, UserRepository userRepo, GroupMemberRepository memberRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
    }

    @Transactional
    public Group createGroup(String name) {
        return groupRepo.save(new Group(name.trim()));
    }

    @Transactional
    public AddMemberResponse addMember(Long groupId, String name) {
        Group group = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        String trimmed = name.trim();
        User user = userRepo.save(new User(trimmed));

        if (!memberRepo.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            memberRepo.save(new GroupMember(group, user));
        }

        return new AddMemberResponse(user.getId(), user.getName());
    }


    @Transactional
    public GroupResponse getGroup(Long groupId) {
        Group group = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<MemberResponse> members = memberRepo.findByGroupId(groupId).stream()
                .map(gm -> new MemberResponse(gm.getUser().getId(), gm.getUser().getName()))
                .toList();

        return new GroupResponse(group.getId(), group.getName(), members);
    }

    @Transactional
    public GroupResponse updateGroupName(Long groupId, String newName) {
        Group group = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }

        group.setName(trimmed);
        Group saved = groupRepo.save(group);

        List<MemberResponse> members = memberRepo.findByGroupId(groupId).stream()
                .map(m -> new MemberResponse(m.getUser().getId(), m.getUser().getName()))
                .toList();

        return new GroupResponse(saved.getId(), saved.getName(), members);
    }

    public PaginatedResponse<GroupResponse> listGroups(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]);
        PageRequest pageRequest = Page.of(page, size, sortOrder);

        Page<Group> groupPage = groupRepo.findAll(pageRequest);

        List<GroupResponse> groupResponses = groupPage.getContent().stream()
                .map(g -> new GroupResponse(
                        g.getId(),
                        g.getName(),
                        listMembersForGroup(g.getId())
                ))
                .toList();

        return new PaginatedResponse<>(
                groupResponses,
                groupPage.getTotalElements(),
                groupPage.getTotalPages(),
                groupPage.getNumber(),
                groupPage.getSize()
        );
    }

    private List<MemberResponse> listMembersForGroup(Long groupId) {
        return memberRepo.findByGroupId(groupId).stream()
                .map(gm -> new MemberResponse(gm.getUser().getId(), gm.getUser().getName()))
                .toList();
    }

}
