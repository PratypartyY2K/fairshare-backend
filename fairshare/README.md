# fairshare

This repository contains `fairshare`, a Spring Boot service for tracking group expenses, computing ledgers and suggested
settlements, and recording confirmed transfers.

Repository structure

- `backend/` — Java Spring Boot application (main service). See `backend/HELP.md` for detailed instructions.
- `frontend/` — optional frontend app (if present)
- `docs/` — additional documentation

Quick start (backend)

1. Build

   mvn -f backend/pom.xml clean package

2. Run

   mvn -f backend/pom.xml spring-boot:run

3. API docs (Swagger UI)

   http://localhost:8080/swagger

For backend-specific instructions, configuration details, endpoints and tests, see `backend/HELP.md`.
