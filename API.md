# Fairshare – API Documentation

Base URL (local):

```
http://localhost:8080
```

---

## Group APIs

### Create Group

```
POST /groups
```

Request:

```json
{ "name": "Roommates" }
```

---

## Member APIs

### Add Member to Group

```
POST /groups/{groupId}/members
```

Request:

```json
{ "userName": "Alice" }
```

Response:

* `204 No Content`

---

## Expense APIs

### Add Expense

```
POST /groups/{groupId}/expenses
```

Request:

```json
{
  "description": "Dinner",
  "amount": 110.0,
  "paidByUserId": 1
}
```

---

## Balance APIs

### Get Net Balances

```
GET /groups/{groupId}/balances
```

Response:

```json
{
  "entries": [
    { "userId": 1, "netBalance": 36.66 },
    { "userId": 2, "netBalance": -13.33 },
    { "userId": 3, "netBalance": -23.33 }
  ]
}
```

---

## Balance Semantics

* Positive value → user should receive money
* Negative value → user owes money

---

## Notes

* No authentication yet
* Validation is minimal by design
* API contracts may evolve as fairness features are added
