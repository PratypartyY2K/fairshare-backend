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
    public ExpenseResponse createExpense(@PathVariable Long groupId, @Valid @RequestBody CreateExpenseRequest req) {
        return service.createExpense(groupId, req.description(), req.amount(), req.payerUserId(), req.participantUserIds());
    }

    @GetMapping("/ledger")
    public LedgerResponse ledger(@PathVariable Long groupId) {
        return service.getLedger(groupId);
    }

    @GetMapping("/expenses")
    public List<ExpenseResponse> listExpenses(@PathVariable Long groupId) {
        return service.listExpenses(groupId);
    }
}
