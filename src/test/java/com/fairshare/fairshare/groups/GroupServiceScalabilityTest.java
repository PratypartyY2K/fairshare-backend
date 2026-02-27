package com.fairshare.fairshare.groups;

import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.groups.api.dto.GroupResponse;
import com.fairshare.fairshare.groups.model.Group;
import com.fairshare.fairshare.groups.model.GroupMember;
import com.fairshare.fairshare.groups.repository.GroupMemberRepository;
import com.fairshare.fairshare.groups.repository.GroupRepository;
import com.fairshare.fairshare.groups.service.GroupService;
import com.fairshare.fairshare.users.model.User;
import com.fairshare.fairshare.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceScalabilityTest {

    @Mock
    private GroupRepository groupRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private GroupMemberRepository memberRepo;
    @Mock
    private EntityManager em;
    @Captor
    private ArgumentCaptor<Set<Long>> idsCaptor;

    @Test
    void listGroups_usesDbPagingAndBatchMemberLookup() {
        GroupService service = new GroupService(groupRepo, userRepo, memberRepo, em);

        Group g1 = new Group("Alpha");
        Group g2 = new Group("Beta");
        ReflectionTestUtils.setField(g1, "id", 11L);
        ReflectionTestUtils.setField(g2, "id", 22L);

        when(groupRepo.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(g1, g2), PageRequest.of(0, 2), 9));

        User u1 = new User("User 1", "u1@example.com");
        User u2 = new User("User 2", "u2@example.com");
        ReflectionTestUtils.setField(u1, "id", 1001L);
        ReflectionTestUtils.setField(u2, "id", 1002L);

        GroupMember gm1 = new GroupMember(g1, u1, GroupMember.Role.OWNER);
        GroupMember gm2 = new GroupMember(g1, u2, GroupMember.Role.MEMBER);
        GroupMember gm3 = new GroupMember(g2, u2, GroupMember.Role.MEMBER);
        when(memberRepo.findByGroupIdInOrderByGroupIdAscUserIdAsc(anySet()))
                .thenReturn(List.of(gm1, gm2, gm3));

        PaginatedResponse<GroupResponse> response = service.listGroups(null, 0, 2, "name,asc", null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalItems()).isEqualTo(9);
        assertThat(response.items().get(0).memberCount()).isEqualTo(2);
        assertThat(response.items().get(1).memberCount()).isEqualTo(1);

        verify(groupRepo, times(1)).findAll(any(PageRequest.class));
        verify(memberRepo, times(1)).findByGroupIdInOrderByGroupIdAscUserIdAsc(anySet());
        verify(memberRepo, never()).findByGroupId(any(Long.class));

        verify(memberRepo).findByGroupIdInOrderByGroupIdAscUserIdAsc(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(11L, 22L);
    }
}
