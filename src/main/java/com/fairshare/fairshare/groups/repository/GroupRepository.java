package com.fairshare.fairshare.groups.repository;

import com.fairshare.fairshare.groups.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Page<Group> findAll(Pageable pageable);

    Page<Group> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Group> findByNameContainingIgnoreCase(String name);
}
