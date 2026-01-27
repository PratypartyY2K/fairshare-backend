package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class ConfirmSettlementsRequest {
    private String confirmationId;

    @NotNull
    private List<Transfer> transfers;

    public ConfirmSettlementsRequest() {
    }

    public ConfirmSettlementsRequest(List<Transfer> transfers) {
        this.transfers = transfers;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public List<Transfer> getTransfers() {
        return transfers;
    }

    public void setTransfers(List<Transfer> transfers) {
        this.transfers = transfers;
    }

    public static class Transfer {
        @NotNull
        private Long fromUserId;
        @NotNull
        private Long toUserId;
        @NotNull
        @Positive
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", example = "10.00")
        private BigDecimal amount;

        public Transfer() {
        }

        public Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
            this.fromUserId = fromUserId;
            this.toUserId = toUserId;
            this.amount = amount;
        }

        public Long getFromUserId() {
            return fromUserId;
        }

        public void setFromUserId(Long fromUserId) {
            this.fromUserId = fromUserId;
        }

        public Long getToUserId() {
            return toUserId;
        }

        public void setToUserId(Long toUserId) {
            this.toUserId = toUserId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
