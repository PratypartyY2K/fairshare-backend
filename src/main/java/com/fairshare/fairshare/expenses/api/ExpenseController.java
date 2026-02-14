package com.fairshare.fairshare.expenses.api;

import com.fairshare.fairshare.common.api.PaginatedResponse;
import com.fairshare.fairshare.expenses.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
                                            value = "{\"description\":\"Groceries\",\"amount\":\"30.75\",\"payerUserId\":10,\"participantUserIds\":[10,11,12]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Shares",
                                            summary = "Split by integer shares (weights)",
                                            value = "{\"description\":\"Dinner\",\"amount\":\"40.00\",\"payerUserId\":5,\"participantUserIds\":[5,6,7],\"shares\":[2,1,1]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "ExactAmounts",
                                            summary = "Split by exact amounts (must sum to total within $0.01)",
                                            value = "{\"description\":\"Party\",\"amount\":\"30.75\",\"payerUserId\":2,\"participantUserIds\":[2,3,4],\"exactAmounts\":[\"15.50\",\"10.00\",\"5.25\"]}"
                                    ),
                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Percentages",
                                            summary = "Split by percentages (must sum to 100)",
                                            value = "{\"description\":\"Rent\",\"amount\":\"1200.00\",\"payerUserId\":8,\"participantUserIds\":[8,9,10],\"percentages\":[\"50.00\",\"25.00\",\"25.00\"]}"
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
    public PaginatedResponse<ExpenseResponse> listExpenses(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return service.listExpenses(groupId, page, size, sort, fromDate, toDate);
    }

    @GetMapping("/settlements")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get settlement transfers for a group", description = "Returns suggested transfers to settle debts in the group")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SettlementResponse.class)))
    public SettlementResponse settlements(@PathVariable Long groupId) {
        return service.getSettlements(groupId);
    }

    @PostMapping("/settlements/confirm")
    @ResponseStatus(HttpStatus.OK) // Changed to 200 OK
    @io.swagger.v3.oas.annotations.Operation(summary = "Confirm settlement transfers", description = "Apply a list of transfers to the ledger as confirmed payments. Returns the confirmation ID and count of applied transfers.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ConfirmSettlementsRequest.class), examples = {
            @io.swagger.v3.oas.annotations.media.ExampleObject(name = "ConfirmWithId", summary = "Confirm with confirmationId for idempotency", value = "{\"confirmationId\":\"confirm-abc-123\",\"transfers\":[{\"fromUserId\":1,\"toUserId\":2,\"amount\":\"10.00\"}]}")
    }))
    @io.swagger.v3.oas.annotations.Parameter(name = "Confirmation-Id", in = io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER, description = "Optional confirmation id (UUID) to make confirmations idempotent; if provided it overrides the body confirmationId when body lacks one", required = false)
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ConfirmSettlementsResponse.class))), // Changed response type
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public ConfirmSettlementsResponse confirmSettlements(@PathVariable Long groupId, @RequestHeader(value = "Confirmation-Id", required = false) String confirmationIdHeader, @Valid @RequestBody ConfirmSettlementsRequest req) {
        return service.confirmSettlements(groupId, req, confirmationIdHeader);
    }

    @GetMapping("/api/confirmation-id") // Changed to a global path
    @io.swagger.v3.oas.annotations.Operation(summary = "Generate a confirmation id (UUID)", description = "Return a fresh confirmation id to be used by the client when confirming settlements; handy for a 'Generate ID' button")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ConfirmationIdResponse.class)))
    public ConfirmationIdResponse generateConfirmationId() {
        return new ConfirmationIdResponse(UUID.randomUUID().toString());
    }

    @GetMapping("/explanations/ledger")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get ledger explanations for a group", description = "Returns a detailed explanation of each user's ledger, including contributing expenses and transfers.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LedgerExplanationResponse.class)))
    public LedgerExplanationResponse getLedgerExplanation(@PathVariable Long groupId) {
        return service.getLedgerExplanation(groupId);
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

    @PatchMapping("/expenses/{expenseId}")
    @io.swagger.v3.oas.annotations.Operation(summary = "Update an expense", description = "Update an existing expense (full replacement of description/amount/splits). Produces an ExpenseUpdated event.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ExpenseResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public ExpenseResponse updateExpense(@PathVariable Long groupId, @PathVariable Long expenseId, @Valid @RequestBody CreateExpenseRequest req) {
        return service.updateExpense(groupId, expenseId, req);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @io.swagger.v3.oas.annotations.Operation(summary = "Void (delete) an expense", description = "Mark an expense as voided and reverse its ledger effects; produces an ExpenseVoided event.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "No Content"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fairshare.fairshare.common.api.ApiError.class)))
    })
    public void voidExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        service.voidExpense(groupId, expenseId);
    }

    @GetMapping("/events")
    @io.swagger.v3.oas.annotations.Operation(summary = "List expense events for a group", description = "Return the event log (ExpenseCreated, ExpenseUpdated, ExpenseVoided, etc.) for auditing")
    public PaginatedResponse<EventResponse> events(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return service.listEvents(groupId, page, size, sort, fromDate, toDate);
    }

    @GetMapping("/confirmed-transfers")
    @io.swagger.v3.oas.annotations.Operation(summary = "List confirmed transfers for a group", description = "Return confirmed transfers; optionally filter by confirmationId for idempotency lookup")
    public PaginatedResponse<ConfirmedTransferResponse> confirmedTransfers(
            @PathVariable Long groupId,
            @RequestParam(required = false) String confirmationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return service.listConfirmedTransfers(groupId, confirmationId, page, size, sort, fromDate, toDate);
    }

}
