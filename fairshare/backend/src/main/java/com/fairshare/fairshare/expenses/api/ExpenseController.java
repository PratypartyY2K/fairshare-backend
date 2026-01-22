package com.fairshare.fairshare.expenses.api;

import com.fairshare.fairshare.expenses.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups/{groupId}")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Create an expense",
            description = "Create an expense with different split modes: exactAmounts, percentages, shares, or equal split. " +
                    "Only one split mode should be provided. If participants are omitted, all group members are used.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CreateExpenseRequest.class),
                            examples = {
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "EqualSplit",
                                            summary = "Equal split among provided participants",
                                            value = "{\"description\":\"Groceries\",\"amount\":30.75,\"paidByUserId\":10,\"participantUserIds\":[10,11,12]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Shares",
                                            summary = "Split by integer shares (weights)",
                                            value = "{\"description\":\"Dinner\",\"amount\":40.00,\"paidByUserId\":5,\"participantUserIds\":[5,6,7],\"shares\":[2,1,1]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "ExactAmounts",
                                            summary = "Split by exact amounts (must sum to total within $0.01)",
                                            value = "{\"description\":\"Party\",\"amount\":30.75,\"paidByUserId\":2,\"participantUserIds\":[2,3,4],\"exactAmounts\":[15.50,10.00,5.25]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Percentages",
                                            summary = "Split by percentages (must sum to 100)",
                                            value = "{\"description\":\"Rent\",\"amount\":1200.00,\"paidByUserId\":8,\"participantUserIds\":[8,9,10],\"percentages\":[50,25,25]}"
                                    )
                            }
                    )
            )
    )
    public ExpenseResponse createExpense(@PathVariable Long groupId, @Valid @RequestBody CreateExpenseRequest req) {
        return service.createExpense(groupId, req);
    }

    @GetMapping("/ledger")
    public LedgerResponse ledger(@PathVariable Long groupId) {
        return service.getLedger(groupId);
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> listExpenses(@PathVariable Long groupId) {
        return service.listExpenses(groupId);
    }

    @GetMapping("/settlements")
    public SettlementResponse settlements(@PathVariable Long groupId) {
        return service.getSettlements(groupId);
    }

    @PostMapping("/settlements/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmSettlements(@PathVariable Long groupId, @Valid @RequestBody ConfirmSettlementsRequest req) {
        service.confirmSettlements(groupId, req.getTransfers());
    }

}
