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
            ),
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(name = "Idempotency-Key", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER, description = "Idempotency key to make create expense requests safe to retry", required = false)
            }
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ExpenseResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public ExpenseResponse createExpense(@PathVariable Long groupId, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @Valid @RequestBody CreateExpenseRequest req) {
        return service.createExpense(groupId, req, idempotencyKey);
    }

    @GetMapping("/ledger")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get ledger for a group", description = "Returns net balances for each user in the group")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LedgerResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Group not found", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public LedgerResponse ledger(@PathVariable Long groupId) {
        return service.getLedger(groupId);
    }

    @GetMapping("/expenses")
    @io.swagger.v3.oas.annotations.Operation(summary = "List expenses for a group")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK")
    public List<ExpenseResponse> listExpenses(@PathVariable Long groupId) {
        return service.listExpenses(groupId);
    }

    @GetMapping("/settlements")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get settlement transfers for a group", description = "Returns suggested transfers to settle debts in the group")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SettlementResponse.class)))
    public SettlementResponse settlements(@PathVariable Long groupId) {
        return service.getSettlements(groupId);
    }

    @PostMapping("/settlements/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @io.swagger.v3.oas.annotations.Operation(summary = "Confirm settlement transfers", description = "Apply a list of transfers to the ledger as confirmed payments")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ConfirmSettlementsRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "No Content"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public void confirmSettlements(@PathVariable Long groupId, @Valid @RequestBody ConfirmSettlementsRequest req) {
        service.confirmSettlements(groupId, req.getTransfers());
    }

    @GetMapping("/owes")
    @io.swagger.v3.oas.annotations.Operation(summary = "How much one user owes another", description = "Returns the amount that `fromUserId` should pay `toUserId` based on recorded expense/payment history (obligations minus confirmed transfers)")
    @io.swagger.v3.oas.annotations.Parameter(name = "fromUserId", description = "User id who would pay", required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "toUserId", description = "User id who would receive payment", required = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = OwesResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public OwesResponse owes(@PathVariable Long groupId, @RequestParam Long fromUserId, @RequestParam Long toUserId) {
        return new OwesResponse(service.amountOwedHistorical(groupId, fromUserId, toUserId));
    }

    @GetMapping("/owes/historical")
    @io.swagger.v3.oas.annotations.Operation(summary = "Historical owes (by expense/payment history)", description = "Computes how much fromUserId owes toUserId based on recorded expenses (where toUserId acted as payer) minus confirmed transfers from fromUserId to toUserId.")
    @io.swagger.v3.oas.annotations.Parameter(name = "fromUserId", description = "User id who would pay", required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "toUserId", description = "User id who would receive payment", required = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = OwesResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public OwesResponse owesHistorical(@PathVariable Long groupId, @RequestParam Long fromUserId, @RequestParam Long toUserId) {
        return new OwesResponse(service.amountOwedHistorical(groupId, fromUserId, toUserId));
    }

}
