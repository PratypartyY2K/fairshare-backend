package com.fairshare.fairshare.groups.service;

import com.fairshare.fairshare.auth.ForbiddenException;
import com.fairshare.fairshare.common.BadRequestException;
import com.fairshare.fairshare.common.NotFoundException;
import com.fairshare.fairshare.common.SortUtils;
import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.api.AddMemberResponse;
import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.api.dto.MemberResponse;
import com.fairshare.fairshare.groups.model.Group;
import com.fairshare.fairshare.groups.model.GroupMember;
import com.fairshare.fairshare.groups.repository.GroupMemberRepository;
import com.fairshare.fairshare.groups.repository.GroupRepository;
import com.fairshare.fairshare.users.model.User;
import com.fairshare.fairshare.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GroupService {
    private final GroupRepository groupRepo;
    private final UserRepository userRepo;
    private final GroupMemberRepository memberRepo;
    private final EntityManager em;

    public GroupService(GroupRepository groupRepo, UserRepository userRepo, GroupMemberRepository memberRepo, EntityManager em) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.em = em;
    }

    @Transactional
    public Group createGroup(String name, Long actorUserId) {
        Group group = groupRepo.save(new Group(name.trim(), actorUserId));
        if (actorUserId != null) {
            User actor = userRepo.findById(actorUserId)
                    .orElseThrow(() -> new NotFoundException("User " + actorUserId + " not found"));
            if (!memberRepo.existsByGroupIdAndUserId(group.getId(), actorUserId)) {
                memberRepo.save(new GroupMember(group, actor, GroupMember.Role.OWNER));
            }
        }
        return group;
    }

    @Transactional
    public AddMemberResponse addMember(Long groupId, Long actorUserId, String name, String email, Long userId) {
        Group group = requireGroup(groupId);
        requireOwner(groupId, actorUserId);

        User user;
        if (userId != null) {
            user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        } else {
            String normalizedEmail = normalizeEmail(email);
            user = userRepo.findByEmailIgnoreCase(normalizedEmail).orElseGet(() -> {
                String trimmedName = name == null ? "" : name.trim();
                if (trimmedName.isBlank()) {
                    throw new BadRequestException("Member name must not be blank when creating a new user");
                }
                return userRepo.save(new User(trimmedName, normalizedEmail));
            });
        }

        if (!memberRepo.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            memberRepo.save(new GroupMember(group, user, GroupMember.Role.MEMBER));
        }

        return new AddMemberResponse(user.getId(), user.getName());
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new BadRequestException("Member email must not be blank");
        }
        return normalized;
    }

    @Transactional
    public GroupResponse getGroup(Long groupId, Long actorUserId) {
        Group group = requireGroup(groupId);
        requireMember(groupId, actorUserId);

        return toGroupResponse(group.getId(), group.getName(), actorUserId);
    }

    @Transactional
    public GroupResponse updateGroupName(Long groupId, Long actorUserId, String newName) {
        Group group = requireGroup(groupId);
        requireOwner(groupId, actorUserId);

        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isBlank()) {
            throw new BadRequestException("Group name must not be blank");
        }

        group.setName(trimmed);
        Group saved = groupRepo.save(group);

        return toGroupResponse(saved.getId(), saved.getName(), actorUserId);
    }

    public PaginatedResponse<GroupResponse> listGroups(Long actorUserId, int page, int size, String sort, String name) {
        String[] sortParams = (sort == null) ? new String[]{"id", "desc"} : sort.split(",");
        String sortProperty = sortParams.length > 0 ? sortParams[0].trim() : "id";
        String sortDirection = sortParams.length > 1 ? sortParams[1].trim() : "desc";

        if ("memberCount".equalsIgnoreCase(sortProperty)) {
            return listGroupsByMemberCount(actorUserId, page, size, sortDirection, name);
        }

        Sort sortOrder = SortUtils.parseSort(sort, "id,desc");
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size, sortOrder);

        Page<Group> groupsPage;
        String trimmedName = name == null ? null : name.trim();
        if (actorUserId != null) {
            groupsPage = (trimmedName == null || trimmedName.isBlank())
                    ? groupRepo.findPageVisibleToUser(actorUserId, pageRequest)
                    : groupRepo.findPageVisibleToUserByName(actorUserId, trimmedName, pageRequest);
        } else {
            groupsPage = (trimmedName == null || trimmedName.isBlank())
                    ? groupRepo.findAll(pageRequest)
                    : groupRepo.findByNameContainingIgnoreCase(trimmedName, pageRequest);
        }
        if (groupsPage.getTotalPages() > 0 && page >= groupsPage.getTotalPages()) {
            int clampedPage = groupsPage.getTotalPages() - 1;
            PageRequest clampedRequest = PageRequest.of(clampedPage, size, sortOrder);
            if (actorUserId != null) {
                groupsPage = (trimmedName == null || trimmedName.isBlank())
                        ? groupRepo.findPageVisibleToUser(actorUserId, clampedRequest)
                        : groupRepo.findPageVisibleToUserByName(actorUserId, trimmedName, clampedRequest);
            } else {
                groupsPage = (trimmedName == null || trimmedName.isBlank())
                        ? groupRepo.findAll(clampedRequest)
                        : groupRepo.findByNameContainingIgnoreCase(trimmedName, clampedRequest);
            }
        }

        List<GroupResponse> groupResponses = toGroupResponses(groupsPage.getContent(), actorUserId);
        return new PaginatedResponse<>(
                groupResponses,
                groupsPage.getTotalElements(),
                groupsPage.getTotalPages(),
                groupsPage.getNumber(),
                groupsPage.getSize()
        );
    }

    @SuppressWarnings("SqlNoDataSourceInspection")
    private PaginatedResponse<GroupResponse> listGroupsByMemberCount(Long actorUserId, int page, int size, String sortDirection, String name) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (name != null && !name.isBlank()) {
            where.append(" AND lower(g.name) LIKE :namePattern ");
        }
        if (actorUserId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM group_members me WHERE me.group_id = g.id AND me.user_id = :actorUserId) ");
        }

        String countSql = "SELECT COUNT(*) FROM groups g " + where;
        var countQuery = em.createNativeQuery(countSql);
        if (name != null && !name.isBlank()) {
            countQuery.setParameter("namePattern", "%" + name.trim().toLowerCase() + "%");
        }
        if (actorUserId != null) {
            countQuery.setParameter("actorUserId", actorUserId);
        }

        long totalItems = ((Number) countQuery.getSingleResult()).longValue();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);
        if (totalPages > 0 && page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        int offset = page * size;

        String dirSql = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        String sql = "SELECT g.id, g.name FROM groups g " + where +
                " ORDER BY (SELECT COUNT(1) FROM group_members gm WHERE gm.group_id = g.id) " + dirSql +
                " LIMIT :limit OFFSET :offset";

        var query = em.createNativeQuery(sql);
        if (name != null && !name.isBlank()) {
            query.setParameter("namePattern", "%" + name.trim().toLowerCase() + "%");
        }
        if (actorUserId != null) {
            query.setParameter("actorUserId", actorUserId);
        }
        query.setParameter("limit", size);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<GroupSummary> summaries = rows.stream()
                .map(r -> new GroupSummary(((Number) r[0]).longValue(), (String) r[1]))
                .toList();
        Map<Long, List<MemberResponse>> membersByGroupId = listMembersForGroups(
                summaries.stream().map(GroupSummary::id).collect(java.util.stream.Collectors.toSet())
        );
        List<GroupResponse> results = summaries.stream()
                .map(summary -> {
                    List<MemberResponse> members = membersByGroupId.getOrDefault(summary.id(), List.of());
                    return new GroupResponse(summary.id(), summary.name(), members, members.size(), actorUserId);
                })
                .toList();

        return new PaginatedResponse<>(results, totalItems, totalPages, page, size);
    }

    private Group requireGroup(Long groupId) {
        return groupRepo.findById(groupId).orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private void requireMember(Long groupId, Long actorUserId) {
        if (actorUserId == null) return;
        if (!memberRepo.existsByGroupIdAndUserId(groupId, actorUserId)) {
            throw new ForbiddenException("User " + actorUserId + " is not a member of group " + groupId);
        }
    }

    private void requireOwner(Long groupId, Long actorUserId) {
        if (actorUserId == null) return;
        if (!memberRepo.existsByGroupIdAndUserIdAndRole(groupId, actorUserId, GroupMember.Role.OWNER)) {
            throw new ForbiddenException("User " + actorUserId + " is not an owner of group " + groupId);
        }
    }

    private List<MemberResponse> listMembersForGroup(Long groupId) {
        return memberRepo.findByGroupId(groupId).stream()
                .map(gm -> new MemberResponse(gm.getUser().getId(), gm.getUser().getName()))
                .toList();
    }

    private Map<Long, List<MemberResponse>> listMembersForGroups(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MemberResponse>> out = new LinkedHashMap<>();
        for (GroupMember gm : memberRepo.findByGroupIdInOrderByGroupIdAscUserIdAsc(groupIds)) {
            Long groupId = gm.getGroup().getId();
            out.computeIfAbsent(groupId, ignored -> new java.util.ArrayList<>())
                    .add(new MemberResponse(gm.getUser().getId(), gm.getUser().getName()));
        }
        return out;
    }

    private List<GroupResponse> toGroupResponses(List<Group> groups, Long actorUserId) {
        Set<Long> groupIds = groups.stream().map(Group::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long, List<MemberResponse>> membersByGroupId = listMembersForGroups(groupIds);
        return groups.stream().map(g -> {
            List<MemberResponse> members = membersByGroupId.getOrDefault(g.getId(), List.of());
            return new GroupResponse(g.getId(), g.getName(), members, members.size(), actorUserId);
        }).toList();
    }

    private GroupResponse toGroupResponse(Long groupId, String groupName, Long actorUserId) {
        List<MemberResponse> members = listMembersForGroup(groupId);
        return new GroupResponse(groupId, groupName, members, members.size(), actorUserId);
    }

    private record GroupSummary(Long id, String name) {}
}
