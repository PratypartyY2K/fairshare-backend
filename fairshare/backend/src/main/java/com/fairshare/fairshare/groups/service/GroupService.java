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
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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
        // Parse sort - we need the raw property/direction to detect memberCount
        String[] sortParams = (sort == null) ? new String[]{"id", "desc"} : sort.split(",");
        String sortProperty = sortParams.length > 0 ? sortParams[0].trim() : "id";
        String sortDirection = sortParams.length > 1 ? sortParams[1].trim() : "desc";

        // If sorting by computed memberCount, execute a native query that orders by member count
        if ("memberCount".equalsIgnoreCase(sortProperty)) {
            // Prepare count query
            String countSql = "SELECT COUNT(*) FROM groups g" + (name != null && !name.isBlank() ? " WHERE lower(g.name) LIKE :namePattern" : "");
            var countQuery = em.createNativeQuery(countSql);
            if (name != null && !name.isBlank()) {
                countQuery.setParameter("namePattern", "%" + name.trim().toLowerCase() + "%");
            }
            Number totalNumber = ((Number) countQuery.getSingleResult());
            long totalItems = totalNumber.longValue();
            int totalPages = (int) Math.ceil((double) totalItems / (double) size);

            // Adjust requested page if out of range
            if (totalPages > 0 && page >= totalPages) {
                page = totalPages - 1;
            }
            int offset = page * size;

            // Build ordering
            String dirSql = "DESC";
            if ("asc".equalsIgnoreCase(sortDirection)) dirSql = "ASC";

            // Native SQL: select group columns and join count
            String sql = "SELECT g.id, g.name FROM groups g " +
                    (name != null && !name.isBlank() ? " WHERE lower(g.name) LIKE :namePattern " : "") +
                    " ORDER BY (SELECT COUNT(1) FROM group_members gm WHERE gm.group_id = g.id) " + dirSql +
                    " LIMIT :limit OFFSET :offset";

            var q = em.createNativeQuery(sql);
            if (name != null && !name.isBlank()) {
                q.setParameter("namePattern", "%" + name.trim().toLowerCase() + "%");
            }
            q.setParameter("limit", size);
            q.setParameter("offset", offset);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();

            List<GroupResponse> results = rows.stream().map(r -> {
                Number idNum = (Number) r[0];
                Long gid = idNum.longValue();
                String gname = (String) r[1];
                List<MemberResponse> members = listMembersForGroup(gid);
                return new GroupResponse(gid, gname, members, members.size());
            }).toList();

            return new PaginatedResponse<>(results, totalItems, totalPages, page, size);
        }

        // Default path: use repository with pageable and SortUtils for safety
        Sort sortOrder = SortUtils.parseSort(sort, "id,desc");
        PageRequest pageRequest = PageRequest.of(page, size, sortOrder);

        Page<Group> groupPage;
        if (name != null && !name.isBlank()) {
            // Load all matching groups and sort/page in-memory to avoid DB-level pagination inconsistencies
            List<Group> allMatches = groupRepo.findByNameContainingIgnoreCase(name.trim());
            // Apply sortOrder in-memory
            List<Group> sorted = allMatches.stream()
                    .sorted((a, b) -> {
                        // support sorting by id or name (default); other properties default to id
                        if (sortOrder.isSorted()) {
                            Sort.Order o = sortOrder.iterator().next();
                            String prop = o.getProperty();
                            int cmp = 0;
                            if ("name".equalsIgnoreCase(prop)) cmp = a.getName().compareToIgnoreCase(b.getName());
                            else if ("id".equalsIgnoreCase(prop)) cmp = a.getId().compareTo(b.getId());
                            else cmp = a.getId().compareTo(b.getId());
                            return o.isAscending() ? cmp : -cmp;
                        }
                        return b.getId().compareTo(a.getId());
                    })
                    .toList();

            int totalItems = sorted.size();
            int totalPages = (int) Math.ceil((double) totalItems / (double) size);
            if (totalPages > 0 && page >= totalPages) page = totalPages - 1;
            int from = Math.max(0, page * size);
            int to = Math.min(from + size, totalItems);
            List<Group> pageContent = sorted.subList(from, to);

            List<GroupResponse> groupResponses = pageContent.stream()
                    .map(g -> {
                        List<MemberResponse> members = listMembersForGroup(g.getId());
                        return new GroupResponse(g.getId(), g.getName(), members, members.size());
                    })
                    .toList();

            return new PaginatedResponse<>(groupResponses, totalItems, totalPages, page, size);
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
