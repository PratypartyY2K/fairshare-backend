package com.fairshare.fairshare.groups.repository;

import com.fairshare.fairshare.groups.model.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Page<Group> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query(value = "SELECT g FROM Group g WHERE EXISTS (SELECT 1 FROM GroupMember gm WHERE gm.group.id = g.id AND gm.user.id = :userId)",
            countQuery = "SELECT COUNT(g) FROM Group g WHERE EXISTS (SELECT 1 FROM GroupMember gm WHERE gm.group.id = g.id AND gm.user.id = :userId)")
    Page<Group> findPageVisibleToUser(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT g FROM Group g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%')) AND EXISTS (SELECT 1 FROM GroupMember gm WHERE gm.group.id = g.id AND gm.user.id = :userId)",
            countQuery = "SELECT COUNT(g) FROM Group g WHERE LOWER(g.name) LIKE LOWER(CONCAT('%', :name, '%')) AND EXISTS (SELECT 1 FROM GroupMember gm WHERE gm.group.id = g.id AND gm.user.id = :userId)")
    Page<Group> findPageVisibleToUserByName(@Param("userId") Long userId, @Param("name") String name, Pageable pageable);
}
