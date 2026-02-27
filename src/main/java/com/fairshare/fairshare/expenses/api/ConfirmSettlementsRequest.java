package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Setter
@Getter
public class ConfirmSettlementsRequest {
    private String confirmationId;

    @NotNull
    private List<Transfer> transfers;

    public ConfirmSettlementsRequest() {
    }

    public ConfirmSettlementsRequest(List<Transfer> transfers) {
        this.transfers = transfers;
    }

    @Setter
    @Getter
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

    }
}
