package com.fairshare.fairshare.groups.service;

import com.fairshare.fairshare.auth.ForbiddenException;
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
import com.fairshare.fairshare.users.User;
import com.fairshare.fairshare.users.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public AddMemberResponse addMember(Long groupId, Long actorUserId, String name, Long userId) {
        Group group = requireGroup(groupId);
        requireOwner(groupId, actorUserId);

        User user;
        if (userId != null) {
            user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
        } else {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isBlank()) {
                throw new IllegalArgumentException("Member name must not be blank");
            }
            user = userRepo.save(new User(trimmed));
        }

        if (!memberRepo.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            memberRepo.save(new GroupMember(group, user, GroupMember.Role.MEMBER));
        }

        return new AddMemberResponse(user.getId(), user.getName());
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
            throw new IllegalArgumentException("Group name must not be blank");
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
        List<Group> all = (name != null && !name.isBlank())
                ? groupRepo.findByNameContainingIgnoreCase(name.trim())
                : groupRepo.findAll();

        if (actorUserId != null) {
            Set<Long> allowedGroupIds = memberRepo.findByUserId(actorUserId).stream()
                    .map(gm -> gm.getGroup().getId())
                    .collect(Collectors.toSet());
            all = all.stream().filter(g -> allowedGroupIds.contains(g.getId())).toList();
        }

        List<Group> sorted = sortGroups(all, sortOrder);
        int totalItems = sorted.size();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalItems / (double) size);

        if (totalPages > 0 && page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int from = Math.max(0, page * size);
        int to = Math.min(from + size, totalItems);
        List<Group> pageContent = from <= to ? sorted.subList(from, to) : List.of();

        List<GroupResponse> groupResponses = pageContent.stream()
                .map(g -> toGroupResponse(g.getId(), g.getName(), actorUserId))
                .toList();

        return new PaginatedResponse<>(groupResponses, totalItems, totalPages, page, size);
    }

    private PaginatedResponse<GroupResponse> listGroupsByMemberCount(Long actorUserId, int page, int size, String sortDirection, String name) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (name != null && !name.isBlank()) {
            where.append(" AND lower(g.name) LIKE :namePattern ");
        }
        if (actorUserId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM group_members me WHERE me.group_id = g.id AND me.user_id = :actorUserId) ");
        }

        //noinspection SqlNoDataSourceInspection
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

        //noinspection SqlNoDataSourceInspection
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

        List<GroupResponse> results = rows.stream().map(r -> {
            Long groupId = ((Number) r[0]).longValue();
            String groupName = (String) r[1];
            return toGroupResponse(groupId, groupName, actorUserId);
        }).toList();

        return new PaginatedResponse<>(results, totalItems, totalPages, page, size);
    }

    private List<Group> sortGroups(List<Group> groups, Sort sortOrder) {
        return groups.stream().sorted((a, b) -> {
            if (sortOrder.isSorted()) {
                Sort.Order order = sortOrder.iterator().next();
                String property = order.getProperty();
                int cmp;
                if ("name".equalsIgnoreCase(property)) cmp = a.getName().compareToIgnoreCase(b.getName());
                else cmp = a.getId().compareTo(b.getId());
                return order.isAscending() ? cmp : -cmp;
            }
            return b.getId().compareTo(a.getId());
        }).toList();
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

    private GroupResponse toGroupResponse(Long groupId, String groupName, Long actorUserId) {
        List<MemberResponse> members = listMembersForGroup(groupId);
        return new GroupResponse(groupId, groupName, members, members.size(), actorUserId);
    }
}
