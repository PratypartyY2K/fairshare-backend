package com.fairshare.fairshare.groups.service;

import com.fairshare.fairshare.common.SortUtils;
import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.Group;
import com.fairshare.fairshare.groups.model.GroupMember;
import com.fairshare.fairshare.groups.repository.GroupMemberRepository;
import com.fairshare.fairshare.groups.repository.GroupRepository;
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

        List<MemberResponse> members = listMembersForGroup(groupId);

        return new GroupResponse(group.getId(), group.getName(), members, members.size());
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

        return new GroupResponse(saved.getId(), saved.getName(), members, members.size());
    }

    public PaginatedResponse<GroupResponse> listGroups(int page, int size, String sort, String name) {
        Sort sortOrder = SortUtils.parseSort(sort, "id,desc");
        PageRequest pageRequest = PageRequest.of(page, size, sortOrder);

        Page<Group> groupPage;
        if (name != null && !name.isBlank()) {
            groupPage = groupRepo.findByNameContainingIgnoreCase(name.trim(), pageRequest);
        } else {
            groupPage = groupRepo.findAll(pageRequest);
        }

        // If the requested page is past the last page but there are results, return the last page
        if (groupPage.getContent().isEmpty() && groupPage.getTotalPages() > 0 && page >= groupPage.getTotalPages()) {
            int lastPage = groupPage.getTotalPages() - 1;
            PageRequest lastPageRequest = PageRequest.of(lastPage, size, sortOrder);
            if (name != null && !name.isBlank()) {
                groupPage = groupRepo.findByNameContainingIgnoreCase(name.trim(), lastPageRequest);
            } else {
                groupPage = groupRepo.findAll(lastPageRequest);
            }
        }

        List<GroupResponse> groupResponses = groupPage.getContent().stream()
                .map(g -> {
                    List<MemberResponse> members = listMembersForGroup(g.getId());
                    return new GroupResponse(
                            g.getId(),
                            g.getName(),
                            members,
                            members.size()
                    );
                })
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
