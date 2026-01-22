package com.fairshare.fairshare.groups;

import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.api.dto.MemberResponse;
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
    public com.fairshare.fairshare.groups.api.AddMemberResponse addMember(Long groupId, String userName) {
        Group group = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        String trimmed = userName.trim();
        User user = userRepo.save(new User(trimmed));

        if (!memberRepo.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            memberRepo.save(new GroupMember(group, user));
        }

        return new com.fairshare.fairshare.groups.api.AddMemberResponse(user.getId(), user.getName());
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

    public List<GroupResponse> listGroups() {
        return groupRepo.findAll().stream()
                .map(g -> new GroupResponse(
                        g.getId(),
                        g.getName(),
                        listMembersForGroup(g.getId())
                ))
                .toList();
    }

    private List<MemberResponse> listMembersForGroup(Long groupId) {
        return memberRepo.findByGroupId(groupId).stream()
                .map(gm -> new MemberResponse(gm.getUser().getId(), gm.getUser().getName()))
                .toList();
    }

}
