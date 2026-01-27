package com.fairshare.fairshare.expenses.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Schema(
        name = "CreateExpenseRequest",
        description = "Request to create an expense. Exactly one split mode (shares, exactAmounts, or percentages) must be provided. If none are provided, the expense is split equally among participants.\n" +
                "- exactAmounts: list of exact money amounts per participant (must sum to total ± $0.01).\n" +
                "- percentages: list of percentages per participant (must sum to 100% ± 0.01).\n" +
                "- shares: list of integer weights (relative shares).\n" +
                "If participants are omitted, all group members are used. Leftover cents are distributed deterministically by ascending userId to guarantee sums equal the total."
)
public class CreateExpenseRequest {
    @NotBlank
    @Schema(description = "Short description of the expense", example = "Groceries")
    private String description;

    @NotNull
    @DecimalMin("0.01")
    @Schema(description = "Total expense amount (two decimals)", type = "string", example = "30.75")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    @NotNull
    @JsonProperty("payerUserId")
    @JsonAlias("paidByUserId")
    @Schema(description = "User id who paid the expense. `paidByUserId` is deprecated; use `payerUserId`.", example = "10")
    private Long payerUserId;

    // participants can be omitted -> default to all group members
    @ArraySchema(schema = @Schema(description = "List of participant user ids (order matters for exact amounts/percentages/shares)", example = "[10,11,12]"))
    private List<Long> participantUserIds;

    // split modes (optional) - only one should be provided
    // shares: relative integer weights, e.g., [2,1,1]
    @ArraySchema(schema = @Schema(description = "Relative integer weights for participants (corresponds to participantUserIds order). e.g., [2,1,1]", minimum = "1", example = "[2,1,1]"))
    private List<@Positive Integer> shares;

    // exact amounts list, corresponding to participants order
    @ArraySchema(schema = @Schema(description = "Exact monetary amounts for participants (must sum to total ± $0.01). Order corresponds to participantUserIds. e.g., [7.25,2.75]", type = "string", example = "[\"7.25\",\"2.75\"]"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<@NotNull BigDecimal> exactAmounts;

    // percentages list (0-100), corresponding to participants order
    @ArraySchema(schema = @Schema(description = "Percentages for participants (must sum to 100% ± 0.01). Order corresponds to participantUserIds. e.g., [50,25,25]", type = "string", example = "[\"50.00\",\"25.00\",\"25.00\"]"))
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<@NotNull BigDecimal> percentages;

    // Default constructor for Jackson
    public CreateExpenseRequest() {
    }

    // Backwards-compatible constructor used by tests and earlier code
    public CreateExpenseRequest(String description, BigDecimal amount, Long payerUserId, List<Long> participantUserIds) {
        this.description = description;
        this.amount = amount;
        this.payerUserId = payerUserId;
        this.participantUserIds = participantUserIds;
    }

    // Full constructor
    public CreateExpenseRequest(String description, BigDecimal amount, Long payerUserId, List<Long> participantUserIds, List<Integer> shares, List<BigDecimal> exactAmounts, List<BigDecimal> percentages) {
        this.description = description;
        this.amount = amount;
        this.payerUserId = payerUserId;
        this.participantUserIds = participantUserIds;
        this.shares = shares;
        this.exactAmounts = exactAmounts;
        this.percentages = percentages;
    }

    // legacy-style accessors to mimic record names used elsewhere
    public String description() {
        return description;
    }

    public BigDecimal amount() {
        return amount;
    }

    public Long payerUserId() {
        return payerUserId;
    }

    public List<Long> participantUserIds() {
        return participantUserIds;
    }

    // getters for new fields
    public List<Integer> getShares() {
        return shares;
    }

    public void setShares(List<Integer> shares) {
        this.shares = shares;
    }

    public List<BigDecimal> getExactAmounts() {
        return exactAmounts;
    }

    public void setExactAmounts(List<BigDecimal> exactAmounts) {
        this.exactAmounts = exactAmounts;
    }

    public List<BigDecimal> getPercentages() {
        return percentages;
    }

    public void setPercentages(List<BigDecimal> percentages) {
        this.percentages = percentages;
    }

    // setters for Jackson
    public void setDescription(String description) {
        this.description = description;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setPayerUserId(Long payerUserId) {
        this.payerUserId = payerUserId;
    }

    public void setParticipantUserIds(List<Long> participantUserIds) {
        this.participantUserIds = participantUserIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateExpenseRequest)) return false;
        CreateExpenseRequest that = (CreateExpenseRequest) o;
        return Objects.equals(description, that.description) && Objects.equals(amount, that.amount) && Objects.equals(payerUserId, that.payerUserId) && Objects.equals(participantUserIds, that.participantUserIds) && Objects.equals(shares, that.shares) && Objects.equals(exactAmounts, that.exactAmounts) && Objects.equals(percentages, that.percentages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, amount, payerUserId, participantUserIds, shares, exactAmounts, percentages);
    }
}
