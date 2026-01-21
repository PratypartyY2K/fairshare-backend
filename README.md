# Fairshare

Fairshare is a group expense management system focused on **long‑term fairness**, not just equal splitting. The project is being built bottom‑up with a strong backend foundation before introducing optimization heuristics or UI polish.

---

## Project Status

**Backend MVP (Core):** Completed
**Frontend:** Not started
**Fairness Engine:** Planned

---

## What Exists Today

* Group creation
* Member management
* Expense tracking
* Equal‑split balance computation
* Deterministic, restart‑safe backend behavior

---

## Tech Stack

* Java + Spring Boot
* Maven
* REST APIs (JSON)
* JPA / Hibernate
* Relational database

---

## High‑Level Architecture

```
Client (curl / Postman / future frontend)
        ↓
Spring Boot REST API
        ↓
Service Layer (business logic)
        ↓
JPA Repositories
        ↓
Database
```

---

## Design Principles

* Correctness before cleverness
* Simple, deterministic core
* Fairness as an additive layer (not a hack)

---

## Documentation

* **API Reference:** see `API.md`
* **Architecture & Design Notes:** see `ARCHITECTURE.md`

---

## Roadmap (Short‑Term)

* Settlement generation (who pays whom)
* Validation & error handling
* Frontend integration

---

*Fairshare is intentionally evolving from a clean core to a fairness‑aware system.*
