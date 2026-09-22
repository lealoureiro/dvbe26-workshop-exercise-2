## Why

API consumers need a list of Greek gods, each with a stable id and a name. The only source of this data, the upstream Greek gods service (`GET /greek`), is slow and unreliable and returns names only, without ids. Serving consumers directly from upstream would couple every request to a dependency we don't control and give them no stable identifier to reference.

## What Changes

- New public, unauthenticated read endpoint `GET /api/v1/gods` returning a plain JSON array of gods, each with only an `id` and a `name`, served exclusively from our own PostgreSQL database.
- Server-side failures on the read endpoint are reported as HTTP 500 with an RFC 9457 Problem Details body; no partial data is returned.
- New PostgreSQL table of gods (numeric id, case-insensitively unique name).
- New scheduled background sync (default every 5 minutes, configurable) that fetches names from upstream and inserts unseen names with a fresh id. Existing gods are never updated or deleted; names that disappear upstream are kept.
- Sync matches names case-insensitively, keeps the stored spelling for existing gods, and stores the first occurrence when a payload contains case-variant duplicates.
- A failed sync (upstream `500`, `504`, or timeout) is logged, leaves stored data untouched, and is retried on the next scheduled run.
- Greenfield project bootstrap: Spring Boot 4 / Java 25 Maven project with the Maven Wrapper.

## Capabilities

### New Capabilities
- `greek-gods-read-api`: Public read endpoint that lists Greek gods (id + name) from our own database, isolated from the upstream service, with Problem Details errors.
- `greek-gods-sync`: Scheduled background sync from the upstream Greek gods service into our database with stable ids, case-insensitive name matching, keep-on-removal, and failure isolation.

### Modified Capabilities
<!-- None: no existing specs in openspec/specs/. -->

## Impact

- **Code:** new Spring Boot application under `src/main/java`, configuration and `schema.sql` under `src/main/resources`, tests under `src/test/java`.
- **Public API:** new endpoint `GET /api/v1/gods` (additive; no existing API).
- **Database:** new table for gods, created via Spring SQL initialization (`schema.sql`).
- **Dependencies (maintainer-approved):** Spring Web MVC (with `RestClient`), Spring Data JDBC, PostgreSQL JDBC driver, Testcontainers (PostgreSQL), WireMock.
- **External systems:** upstream `https://my-json-server.typicode.com/jabrena/latency-problems` (`GET /greek`) called only by the sync job; contract in `oas/provided/my-json-server-oas.yaml` (read-only).

## Traceability

- **Source issue:** https://github.com/lealoureiro/dvbe26-workshop-exercise-2/issues/3
- **Retrieved:** 2026-09-22T20:57:40Z. Accessible comments: 2 (provider total 2, reconciled).
  - Issue body: User Story, scope, and initial Gherkin.
  - Functional Specification: https://github.com/lealoureiro/dvbe26-workshop-exercise-2/issues/3#issuecomment-5783928841
  - Acceptance Criteria: https://github.com/lealoureiro/dvbe26-workshop-exercise-2/issues/3#issuecomment-5783999471
- **Maintainer decisions (during `/create-spec`, 2026-09-22):** endpoint `GET /api/v1/gods` with a plain-array response; RFC 9457 Problem Details for errors; `BIGINT` identity ids; schema via Spring `schema.sql` (Flyway not approved); dependencies as listed under Impact.
- **Derivation direction:** issue → OpenSpec (one-way). The issue is not updated from this change. Endpoint and schema decisions made here resolve the "Unknowns" in the Functional Specification but are not written back to the issue.
