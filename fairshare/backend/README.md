# Backend — Getting Started

This document provides concise, up-to-date instructions for building, running, and testing the backend service in this
repository.

Last verified: 2026-02-13

## Overview

fairshare is a Spring Boot (Java) service for tracking group expenses, calculating ledgers and settlements, and
recording confirmed transfers.

## Prerequisites

- Java 21 (project property `<java.version>` in `backend/pom.xml`)
- Maven (use `mvn` from the command line)
- PostgreSQL for production (the default datasource URL in `application.yml` points to
  `jdbc:postgresql://localhost:5432/fairshare`). Tests use H2 in-memory DB.

## Build

From repository root (or `backend/`):

mvn -f backend/pom.xml clean package

or to run in development mode:

mvn -f backend/pom.xml spring-boot:run

The application main class is `com.fairshare.fairshare.FairshareApplication`.

## Configuration

Default configuration is in `backend/src/main/resources/application.yml`.
Key defaults:

- server.port: 8080
- spring.datasource.url: jdbc:postgresql://localhost:5432/fairshare
- spring.datasource.username/password: fairshare_user/fairshare_pass

You can override properties via environment variables or command-line properties (e.g., `-Dserver.port=9090`).

## API Documentation (OpenAPI / Swagger)

The project includes Springdoc OpenAPI and exposes a Swagger UI at: http://localhost:8080/swagger

## Main Endpoints

- GET /health — service health
- GET / — root info

Groups (base path: /groups)

- POST /groups — create a group
- GET /groups — list groups (supports pagination, sorting and filtering)
- GET /groups/{groupId} — get group details
- PATCH /groups/{groupId} — update group name
- POST /groups/{groupId}/members — add a member

Expenses (group-scoped: /groups/{groupId})

- POST /groups/{groupId}/expenses — create an expense (supports equal, shares, exact amounts, percentages)
- GET /groups/{groupId}/expenses — list expenses
- GET /groups/{groupId}/ledger — get ledger (net balances)
- GET /groups/{groupId}/settlements — get suggested settlement transfers
- POST /groups/{groupId}/settlements/confirm — confirm (apply) settlement transfers (records transfers)
- GET /groups/{groupId}/owes — compute owes using settlement suggestions
- GET /groups/{groupId}/owes/historical — compute owes from recorded expense/payment history

## Features (complete list)

This section enumerates the main features and endpoints the backend exposes. Use the Swagger
UI (http://localhost:8080/swagger) for interactive exploration.

Groups

- Create group: `POST /groups` — create a group by name.
- List groups: `GET /groups` — paginated listing. Supports `page` (0-based), `pageSize`, `sort` (`property,asc|desc`),
  and `name` (case-insensitive substring filter). Response includes `memberCount` and `members`.
- Get group: `GET /groups/{groupId}` — full group details including `members` and `memberCount`.
- Update group name: `PATCH /groups/{groupId}` — rename a group.
- Add member: `POST /groups/{groupId}/members` — adds a member (users are created implicitly by name when added).

Expenses & Ledger

- Create expense: `POST /groups/{groupId}/expenses` — create an expense. Supports four split modes (only one may be
  provided per request):
    - equal — equal split among participants (default when no split provided)
    - shares — integer share weights (e.g., [2,1,1])
    - exactAmounts — explicit per-user amounts (must sum to total within $0.01 tolerance)
    - percentages — percentage split (must sum to 100% within tolerance)
      The API enforces currency normalization (scale=2, RoundingMode.HALF_UP) and distributes rounding leftover
      deterministically by user id ascending.
    - Optional `Idempotency-Key` header allows safe retrying of create requests (the server stores the key on the
      created expense and returns the existing expense when the same key is reused).
- Update expense: `PATCH /groups/{groupId}/expenses/{expenseId}` — replace description/amount/splits; ledger and
  participant records are updated consistently and an `ExpenseUpdated` event is emitted.
- Void expense: `DELETE /groups/{groupId}/expenses/{expenseId}` — mark expense as voided, revert its ledger effects and
  emit `ExpenseVoided` event.

Ledger, Settlements & Transfers

- Get ledger: `GET /groups/{groupId}/ledger` — returns net balances per user in the group.
- Get settlements (suggested): `GET /groups/{groupId}/settlements` — computes suggested transfers to settle the ledger
  using an internal settlement algorithm.
- Confirm settlements: `POST /groups/{groupId}/settlements/confirm` — apply transfers as confirmed payments. Supports
  `confirmationId` either in the body or via `Confirmation-Id` header to make confirmations idempotent. Returns a
  confirmation id and number of applied transfers.
- List confirmed transfers: `GET /groups/{groupId}/confirmed-transfers` — paginated listing of historical confirmed
  transfers; optional `confirmationId` filter and date range filters.

Owes and Explanations

- Owes (quick): `GET /groups/{groupId}/owes?fromUserId=X&toUserId=Y` — compute what X owes Y from suggested settlements.
- Owes historical: `GET /groups/{groupId}/owes/historical?fromUserId=X&toUserId=Y` — compute using recorded expenses and
  confirmed transfers (historical obligations minus payments).
- Ledger explanation: `GET /groups/{groupId}/explanations/ledger` — detailed per-user contribution list (expenses paid,
  shares, transfers sent/received) with timestamps for auditing.

Events & Auditing

- List expense events (audit log): `GET /groups/{groupId}/events` — paginated list of expense events (ExpenseCreated,
  ExpenseUpdated, ExpenseVoided, etc.) with payloads and timestamps.
- The service emits events for create/update/void actions and persists them to the DB for auditing.

Pagination, Sorting & Filtering

- Most list endpoints are paginated and return a `PaginatedResponse<T>` with `items`, `totalItems`, `totalPages`,
  `currentPage`, and `pageSize`.
- Sorting uses the `sort` parameter of the form `property,asc|desc`. The `SortUtils` helper safely parses sort strings
  and falls back to defaults when invalid.
- `memberCount` is a computed property in `GET /groups` responses and is supported as a sorting key — when sorting by
  `memberCount` the service uses a native query path because `memberCount` is not a persisted entity field. Attempting
  to sort by `memberCount` directly with JPA repository methods will produce an error ("No property 'memberCount' found
  for type 'Group'").

Data model notes & validations

- Users are created implicitly when a member is added by name. Multiple users with the same display name are allowed (
  users are identified internally by numeric id).
- Expense inputs are validated: participants must be unique and members of the group; exactly one split mode may be
  provided; amounts/percentages/shares must be internally consistent.
- Currency handling: all monetary values are normalized to 2 decimal places (scale=2) using `RoundingMode.HALF_UP` for
  normalization and deterministic rounding behavior for splits.
- Idempotency for expense creation: the repository stores an `idempotencyKey` on the expense and the create path will
  return an existing expense when the same key is provided.

Utilities and developer aids

- Swagger/OpenAPI UI: `http://localhost:8080/swagger`
- `GET /api/confirmation-id` (via `GET /groups/{groupId}/api/confirmation-id`) — convenience endpoint to generate a UUID
  confirmation id for use when confirming settlements.

## Tests

Run unit/integration tests with:

mvn -f backend/pom.xml test

Tests run against an in-memory H2 database. A set of integration tests exercise pagination, sorting, and name-filter
behavior
for the Groups API (see `backend/src/test/java/com/fairshare/fairshare/groups`).

If you see failures related to paging and name filtering (e.g., expected last-page behavior), ensure that the test
database
state matches the assumptions the test uses (tests create their own sample data). See the test classes for sample
fixtures.

## Troubleshooting

- If you encounter compilation errors, ensure Lombok annotation processing is enabled in your IDE.
- If the service fails to connect to Postgres, either start a local Postgres instance or set `spring.datasource.url` to
  a reachable DB.
- Sorting by `memberCount` may return a server error if used with a direct JPA Sort because `memberCount` is not a real
  entity property; the service maps that sort to a native query. If you hit `No property 'memberCount' found` in
  stacktraces,
  check that you are calling the `/groups` endpoint (the service handles `memberCount` specially) and not directly
  invoking
  a repository method.

## Contributing

Please open issues or PRs with changes. Follow the existing code style and add tests for new behavior.
