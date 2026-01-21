# Fairshare – Architecture & Design

---

## Domain Model

### Group

* Owns members and expenses

### User (Member)

* Belongs to a single group (current scope)
* Participates in expense splitting

### Expense

* Amount paid by a user
* Associated with a group

---

## Balance Computation (Current)

Algorithm:

1. Compute total expenses in group
2. Equal share = total / number of members
3. For each user:

```
netBalance = totalPaidByUser − equalShare
```

This ensures:

* Deterministic results
* No ordering dependence
* Simple correctness proofs

---

## What Is Explicitly Not Implemented

* Settlement minimization graph
* Income‑aware splitting
* Fairness scores
* Recurring expenses
* Multi‑currency support
* Authentication / authorization

These are deferred intentionally.

---

## Future Extensions (Planned)

### Settlement Engine

* Reduce N balances into minimal transactions
* Preserve net balance invariants

### Fairness Engine

* Track historical burden
* Adjust future splits
* Keep system explainable

---

## Philosophy

Fairshare is designed to evolve **layer by layer**:

```
Correct Core → Settlements → Fairness → UX
```

Each layer must preserve correctness of the previous one.

---

*End of architecture notes.*
