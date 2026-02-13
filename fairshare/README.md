# fairshare

This repository contains `fairshare`, a Spring Boot service for tracking group expenses, computing ledgers and suggested
settlements, and recording confirmed transfers.

Features (summary)

This project implements a backend REST service with the following capabilities:

- Manage groups and members (create, list, update, add members)
- Create, update and void expenses with flexible split modes: equal, shares, exact amounts, percentages
- Compute per-user ledgers and suggest settlement transfers
- Confirm (record) transfers as payments and list confirmed transfers
- Audit trail: expense events (created/updated/voided) and ledger explanations
- Pagination, sorting (including computed `memberCount`), and filtering on list endpoints
- Idempotent expense creation via `Idempotency-Key` and idempotent settlement confirmations via `confirmationId`

For the complete, up-to-date feature list and API docs, see `backend/README.md` and the Swagger UI at
`http://localhost:8080/swagger`.

Repository structure

- `backend/` — Java Spring Boot application (main service). See `backend/README.md` for detailed instructions, examples
  and API notes.
- `frontend/` — optional frontend app (if present)
- `docs/` — additional documentation

Quick start (backend)

1. Build

   mvn -f backend/pom.xml clean package

2. Run

   mvn -f backend/pom.xml spring-boot:run

3. API docs (Swagger UI)

   http://localhost:8080/swagger

Notes and recent behavior

- Group responses now include `memberCount` in addition to the `members` array. Example GET /groups returns objects
  like:
  {
  "id": 123,
  "name": "Friends",
  "members": [ ... ],
  "memberCount": 4
  }

- Pagination: use `page` (zero-based) and `pageSize`. For example `?page=0&pageSize=10` returns the first page. If you
  request a page beyond the last page, the service will return the last available page of results.

- Sorting: paginated endpoints accept a `sort` parameter of the form `property,asc|desc` (for example `sort=id,desc` or
  `sort=name,asc`). The `memberCount` property is supported (it is computed) but is handled specially by the service.

- Name filtering (when supported) performs a case-insensitive substring match: `?name=club` will match group names
  containing "club" in any casing.

For backend-specific instructions, configuration details, endpoints and tests, see `backend/README.md`.
