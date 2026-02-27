package com.fairshare.fairshare.groups.repository;

import com.fairshare.fairshare.groups.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    boolean existsByGroupIdAndUserIdAndRole(Long groupId, Long userId, GroupMember.Role role);
    List<GroupMember> findByUserId(Long userId);
    List<GroupMember> findByGroupId(Long groupId);

    @Query("SELECT gm FROM GroupMember gm WHERE gm.group.id IN :groupIds ORDER BY gm.group.id ASC, gm.user.id ASC")
    List<GroupMember> findByGroupIdInOrderByGroupIdAscUserIdAsc(@Param("groupIds") Collection<Long> groupIds);
}
