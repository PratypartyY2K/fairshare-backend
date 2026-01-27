package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConfirmedTransferRepository extends JpaRepository<ConfirmedTransfer, Long> {
    Optional<ConfirmedTransfer> findByGroupIdAndConfirmationId(Long groupId, String confirmationId);

    List<ConfirmedTransfer> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<ConfirmedTransfer> findByGroupIdAndConfirmationIdOrderByCreatedAtDesc(Long groupId, String confirmationId);

    @Query("SELECT COALESCE(SUM(ct.amount), 0) FROM ConfirmedTransfer ct WHERE ct.groupId = ?1 AND ct.fromUserId = ?2 AND ct.toUserId = ?3")
    BigDecimal sumConfirmedAmount(Long groupId, Long fromUserId, Long toUserId);

    int countByGroupIdAndConfirmationId(Long groupId, String confirmationId);
}
