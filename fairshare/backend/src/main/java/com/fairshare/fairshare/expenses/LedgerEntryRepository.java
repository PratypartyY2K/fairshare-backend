package com.fairshare.fairshare.expenses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Optional<LedgerEntry> findByGroupIdAndUserId(Long groupId, Long userId);

    List<LedgerEntry> findByGroupIdOrderByUserIdAsc(Long groupId);
}
