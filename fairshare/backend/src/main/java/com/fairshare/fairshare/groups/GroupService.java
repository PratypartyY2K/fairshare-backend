package com.fairshare.fairshare.groups;

import com.fairshare.fairshare.groups.api.GroupResponse;
import com.fairshare.fairshare.users.User;
import com.fairshare.fairshare.users.UserRepository;
import jakarta.transaction.Transactional;
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
    public void addMember(Long groupId, String userName) {
        Group group = groupRepo.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        User user = userRepo.save(new User(userName.trim()));

        if (memberRepo.existsByGroupIdAndUserId(group.getId(), user.getId())) return; // idempotent-ish

        memberRepo.save(new GroupMember(group, user));
    }

    @Transactional
    public GroupResponse getGroup(Long groupId) {
        Group group = groupRepo.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<GroupResponse.MemberDto> members = memberRepo.findByGroupId(groupId).stream()
            .map(m -> new GroupResponse.MemberDto(m.getUser().getId(), m.getUser().getName()))
            .toList();

        return new GroupResponse(group.getId(), group.getName(), members);
    }
}
