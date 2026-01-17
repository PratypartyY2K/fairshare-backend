package com.fairshare.fairshare.groups;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    List<GroupMember> findByGroupId(Long groupId);
}
