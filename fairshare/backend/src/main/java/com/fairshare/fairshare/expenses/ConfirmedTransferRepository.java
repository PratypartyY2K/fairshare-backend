package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfirmedTransferRepository extends JpaRepository<ConfirmedTransfer, Long> {
    List<ConfirmedTransfer> findByGroupIdAndConfirmationId(Long groupId, String confirmationId);

    List<ConfirmedTransfer> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<ConfirmedTransfer> findByGroupIdAndConfirmationIdOrderByCreatedAtDesc(Long groupId, String confirmationId);

    @Query("SELECT COALESCE(SUM(ct.amount), 0) FROM ConfirmedTransfer ct WHERE ct.groupId = ?1 AND ct.fromUserId = ?2 AND ct.toUserId = ?3")
    BigDecimal sumConfirmedAmount(Long groupId, Long fromUserId, Long toUserId);

    int countByGroupIdAndConfirmationId(Long groupId, String confirmationId);

    List<ConfirmedTransfer> findByGroupIdAndFromUserId(Long groupId, Long fromUserId);

    List<ConfirmedTransfer> findByGroupIdAndToUserId(Long groupId, Long toUserId);

    Page<ConfirmedTransfer> findByGroupId(Long groupId, Pageable pageable);

    Page<ConfirmedTransfer> findByGroupIdAndConfirmationId(Long groupId, String confirmationId, Pageable pageable);

    Page<ConfirmedTransfer> findByGroupIdAndCreatedAtBetween(Long groupId, Instant fromDate, Instant toDate, Pageable pageable);
}
