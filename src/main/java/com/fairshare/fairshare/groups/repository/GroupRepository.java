package com.fairshare.fairshare.groups.repository;

import com.fairshare.fairshare.groups.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByNameContainingIgnoreCase(String name);
}
