package com.fairshare.fairshare.expenses;

import com.fairshare.fairshare.expenses.model.ConfirmedTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ConfirmedTransferRepository extends JpaRepository<ConfirmedTransfer, Long> {
    @Query("select coalesce(sum(ct.amount), 0) from ConfirmedTransfer ct where ct.groupId = :groupId and ct.fromUserId = :from and ct.toUserId = :to")
    BigDecimal sumConfirmedAmount(@Param("groupId") Long groupId, @Param("from") Long fromUserId, @Param("to") Long toUserId);

    Optional<ConfirmedTransfer> findByGroupIdAndConfirmationId(Long groupId, String confirmationId);
}
