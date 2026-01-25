# Backend — Getting Started

This document provides concise, up-to-date instructions for building, running, and testing the backend service in this
repository.

Last verified: 2026-01-25

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
- GET /groups — list groups
- GET /groups/{groupId} — get group
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

## Tests

Run unit/integration tests with:

mvn -f backend/pom.xml test

Tests run against an in-memory H2 database.

## Troubleshooting

- If you encounter compilation errors, ensure Lombok annotation processing is enabled in your IDE.
- If the service fails to connect to Postgres, either start a local Postgres instance or set `spring.datasource.url` to
  a reachable DB.

## Contributing

Please open issues or PRs with changes. Follow the existing code style and add tests for new behavior.
