# Fairshare Backend

Spring Boot backend for group expense tracking, ledger calculation, settlement confirmation, and audit history.

## Stack

- Java 21
- Spring Boot 3.5.7
- Maven
- Spring Data JPA
- Flyway
- PostgreSQL for local/dev
- H2 for tests

## Run Locally

Create the local database once:

```bash
createdb fairshare
psql -c "CREATE USER fairshare_user WITH PASSWORD 'fairshare_pass';"
psql -c "GRANT ALL PRIVILEGES ON DATABASE fairshare TO fairshare_user;"
```

Then start the service:

```bash
mvn spring-boot:run
```

Default local configuration from [application.yml](/Users/pratyushkumar/Desktop/Pratyush/faireshare-mono-repo/fairshare-backend/src/main/resources/application.yml):

- Port: `8080`
- JDBC URL: `jdbc:postgresql://localhost:5432/fairshare`
- Username: `fairshare_user`
- Password: `fairshare_pass`
- Swagger UI: `http://localhost:8080/swagger`

## Database Notes

The backend now expects schema changes to be managed by Flyway, not by Hibernate auto-update.

- `spring.jpa.hibernate.ddl-auto=validate`
- Flyway is enabled
- `baseline-on-migrate=true`

That means:

- On a fresh database, Flyway creates and migrates the schema.
- On an older non-empty local database, Flyway baselines it and then applies tracked migrations.
- If local schema drift exists outside Flyway, startup will fail fast during validation instead of silently mutating tables.

Migrations live in:

- [src/main/resources/db/migration](/Users/pratyushkumar/Desktop/Pratyush/faireshare-mono-repo/fairshare-backend/src/main/resources/db/migration)

## Main Endpoints

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

Expenses and ledger:

- `POST /groups/{groupId}/expenses`
- `GET /groups/{groupId}/expenses`
- `PATCH /groups/{groupId}/expenses/{expenseId}`
- `DELETE /groups/{groupId}/expenses/{expenseId}`
- `GET /groups/{groupId}/ledger`

Settlements and transfers:

- `GET /groups/{groupId}/settlements`
- `POST /groups/{groupId}/settlements/confirm`
- `GET /groups/{groupId}/confirmed-transfers`
- `GET /groups/{groupId}/api/confirmation-id`

Audit and explanations:

- `GET /groups/{groupId}/events`
- `GET /groups/{groupId}/explanations/ledger`
- `GET /groups/{groupId}/owes`
- `GET /groups/{groupId}/owes/historical`

## Auth Model

The service supports header-based actor identity with `X-User-Id`.

Current local default:

- `fairshare.auth.required=false`

When auth is enabled:

- The actor can create groups
- Group creators are added as `OWNER`
- Owners can rename groups and add members
- Members can read group-scoped data
- Non-members get `403`

## API Behaviors Worth Knowing

- Group listing supports pagination, sorting, and case-insensitive `name` filtering.
- `pageSize` is accepted as an alias for `size` on group listing.
- When a requested page is out of range for filtered groups, the service clamps to the last available page.
- Money values are represented as decimal strings in JSON to avoid precision loss.
- Expense creation supports idempotency via the `Idempotency-Key` header.
- Settlement confirmation supports idempotency via request body `confirmationId` or `Confirmation-Id` header.

## Tests

Run the backend test suite:

```bash
mvn test
```

Targeted integration tests that are useful when changing core behavior:

```bash
mvn -Dtest=GroupFilterAndPaginationIntegrationTest,ConfirmSettlementsIntegrationTest,EventsAndTransfersIntegrationTest,PaginationIntegrationTest test
```

Tests run against H2 using [src/test/resources/application.yml](/Users/pratyushkumar/Desktop/Pratyush/faireshare-mono-repo/fairshare-backend/src/test/resources/application.yml).

## Troubleshooting

If startup fails with Flyway or schema validation errors:

- Check that PostgreSQL is running and reachable.
- Make sure you are using the expected local database.
- If the database was manually changed outside migrations, either repair it with a new Flyway migration or reset the local database.

If startup fails because the database is missing:

```bash
createdb fairshare
```

If the service builds but your IDE shows Lombok-related compile errors, enable annotation processing.
