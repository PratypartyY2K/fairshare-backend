# Fairshare Backend

The Fairshare backend is a ledger-oriented Spring Boot service for group expense accounting.

It is designed around a simple idea: shared-expense systems should be explainable. Instead of treating balances as opaque totals, the service records expenses, ledger effects, settlement confirmations, and event history in a way that makes outcomes reproducible and inspectable.

## What This Service Optimizes For

- Correctness: balances are derived from explicit accounting behavior rather than ad hoc client-side math.
- Explainability: users can inspect how expenses and transfers contributed to a current balance.
- Determinism: money handling, split calculations, and leftover-cent distribution are stable and predictable.
- Safety under retries: create and confirm flows support idempotent behavior.
- Auditability: expense mutations and transfers are preserved as historical records.

## System Model

### Ledger-Backed Balances

The service computes group balances from stored ledger entries and expense participation data. Current state is not just a UI convenience layer over totals; it is derived from persisted accounting effects.

### Event History

Expense lifecycle actions produce auditable events such as:

- `ExpenseCreated`
- `ExpenseUpdated`
- `ExpenseVoided`
- `TransferConfirmed`

This makes the backend useful for debugging, trust, and future reporting.

### Deterministic Money Rules

Money values are normalized to scale 2 with explicit rounding behavior. Split calculations support equal, exact-amount, percentage, and share-based modes, with predictable leftover-cent distribution so repeated calculations converge to the same result.

### Idempotent Writes

- Expense creation supports the `Idempotency-Key` header.
- Settlement confirmation supports caller-provided confirmation IDs and safe retry behavior.

That keeps write operations resilient to duplicate submissions and client retries.

## Tech Stack

- Java 21
- Spring Boot 3.5.7
- Maven
- Spring Data JPA
- Flyway
- PostgreSQL for local development
- H2 for tests
- Springdoc OpenAPI / Swagger UI

## Main Capabilities

### Groups And Membership

- Create groups
- List groups with filtering, sorting, and pagination
- Rename groups
- Add members to existing groups

### Expenses

- Create expenses with multiple split modes
- List expenses with pagination and sorting
- Update expenses
- Void expenses while reversing ledger effects

### Ledger And Settlements

- Compute per-user ledger balances for a group
- Compute suggested settlement transfers
- Confirm transfers and persist settlement history
- Query current and historical owes relationships

### History And Explainability

- Return group event history
- Return confirmed transfer history
- Explain balances at the user level from recorded expenses and transfers

## Local Run

### 1. Create the local database

```bash
createdb fairshare
psql -c "CREATE USER fairshare_user WITH PASSWORD 'fairshare_pass';"
psql -c "GRANT ALL PRIVILEGES ON DATABASE fairshare TO fairshare_user;"
```

### 2. Start the service

```bash
./mvnw spring-boot:run
```

Default local configuration from [src/main/resources/application.yml](/Users/pratyushkumar/Desktop/Pratyush/faireshare-mono-repo/fairshare-backend/src/main/resources/application.yml):

- Port: `8080`
- JDBC URL: `jdbc:postgresql://localhost:5432/fairshare`
- Username: `fairshare_user`
- Password: `fairshare_pass`
- Swagger UI: `http://localhost:8080/swagger`

## API Surface

Health:

- `GET /`
- `GET /health`

Users:

- `POST /users`
- `GET /users/{userId}`

Groups:

- `POST /groups`
- `GET /groups`
- `GET /groups/{groupId}`
- `PATCH /groups/{groupId}`
- `POST /groups/{groupId}/members`

Expenses, ledger, and settlements:

- `POST /groups/{groupId}/expenses`
- `GET /groups/{groupId}/expenses`
- `PATCH /groups/{groupId}/expenses/{expenseId}`
- `DELETE /groups/{groupId}/expenses/{expenseId}`
- `GET /groups/{groupId}/ledger`
- `GET /groups/{groupId}/settlements`
- `POST /groups/{groupId}/settlements/confirm`
- `GET /groups/{groupId}/confirmed-transfers`
- `GET /groups/{groupId}/api/confirmation-id`

Audit and explanation endpoints:

- `GET /groups/{groupId}/events`
- `GET /groups/{groupId}/explanations/ledger`
- `GET /groups/{groupId}/owes`
- `GET /groups/{groupId}/owes/historical`

Interactive API docs are exposed through Swagger at `http://localhost:8080/swagger`.

## Behavioral Guarantees

- Group listing supports case-insensitive name filtering plus pagination and sorting.
- `pageSize` is accepted as an alias for `size` on group listing.
- Out-of-range filtered pages are clamped to the last available page.
- JSON money values are returned as decimal strings to avoid precision loss in clients.
- Expense creation accepts one split mode at a time: exact amounts, percentages, shares, or equal split.
- Settlement confirmations can be retried safely with the same confirmation ID.

## Authentication Model

The service supports header-based actor identity via `X-User-Id`.

Current local default:

- `fairshare.auth.required=false`

When auth is required:

- group creators become `OWNER`
- owners can rename groups and add members
- group members can read group-scoped data
- non-members receive `403`

This is intentionally lightweight for local development. It is an authorization-aware backend with a simple actor model, not yet a full end-user authentication system.

## Database And Schema Management

Schema changes are managed with Flyway rather than Hibernate auto-mutation.

- `spring.jpa.hibernate.ddl-auto=validate`
- Flyway is enabled
- `baseline-on-migrate=true`

Operationally, that means:

- a fresh database is created and migrated through tracked migrations
- an older non-empty local database can be baselined and migrated forward
- schema drift outside migrations fails fast during startup instead of being silently patched

## Testing

Run the full test suite:

```bash
./mvnw test
```

Useful targeted integration runs:

```bash
./mvnw -Dtest=GroupFilterAndPaginationIntegrationTest,ConfirmSettlementsIntegrationTest,EventsAndTransfersIntegrationTest,PaginationIntegrationTest test
```

Tests run against H2 using [src/test/resources/application.yml](/Users/pratyushkumar/Desktop/Pratyush/faireshare-mono-repo/fairshare-backend/src/test/resources/application.yml).

## Troubleshooting

If startup fails with Flyway or schema validation errors:

- make sure PostgreSQL is running
- verify the database name, username, and password match local config
- if the schema was changed manually, repair it with a migration or recreate the local database

If the database does not exist:

```bash
createdb fairshare
```

If your IDE reports Lombok-related compile issues, enable annotation processing.
