## Why

Greek god reference data is obtainable only from a third-party JSON service whose latency and availability are outside this project's control, and which returns bare names carrying no identifier. Any consumer reading it inherits that service's response time and downtime on every request, and has nothing stable to store or reference a specific god by.

This change establishes the catalogue that consumers read from, and the identifiers it publishes. It deliberately stops short of populating that catalogue from upstream, which is `greek-gods-upstream-sync`.

## What Changes

- Introduce a persisted catalogue of Greek gods, each holding a name and an identifier minted by this service.
- Expose `GET /api/v1/gods/greek`, serving the catalogue as a JSON array of `{id, name}` records. Public, no authentication, path-versioned.
- Serve every request from the catalogue alone. No request path may reach the third-party service.
- Treat an empty catalogue as a valid served state, returning a successful empty result rather than an error.
- Author `oas/greekController-oas.yaml`, currently an empty file, as the OpenAPI 3.0.3 contract matching the served behaviour.
- Scaffold the project: Maven build and wrapper, Spring Boot application, Flyway migration for `greek_god`, and Testcontainers-backed test setup. **The repository currently has no `pom.xml` and no `mvnw`**, though `AGENTS.md` documents `./mvnw clean verify` as the verification command.

Not a breaking change: no published interface exists yet.

The scaffolding bullet is enabling work and carries no acceptance criterion by design. Specifications describe externally observable behaviour, and a working build is not behaviour a consumer can observe, so no scenario is expected for it. Its absence from the specification is deliberate rather than an omission.

## Capabilities

### New Capabilities

- `greek-gods-catalogue`: what the service publishes about Greek gods and the guarantees attached to it — the response shape, the permanence and uniqueness of identifiers, independence from the third-party service at request time, and the empty-catalogue state.

### Modified Capabilities

None. No specification exists yet in `openspec/specs/`.

## Impact

- **New:** Maven build, Spring Boot application, `greek_god` schema and its Flyway migration, the REST read path, and `oas/greekController-oas.yaml`.
- **New runtime dependency:** PostgreSQL. Ephemeral — Testcontainers for tests, a developer-operated container locally; no shared or hosted instance exists.
- **New build dependencies:** Spring Boot 4.0.x (Web, Data/JDBC), Flyway, Testcontainers, PostgreSQL driver. `AGENTS.md` routes dependency, build-configuration, schema, and API-shape decisions through the repository maintainer, so this change requires that sign-off before implementation.
- **Consumers:** none today. The identifier guarantees exist to protect future ones.
- **Downstream:** `greek-gods-upstream-sync` depends on the schema and the identity rules established here.

## Traceability

| Field | Value |
| --- | --- |
| Source issue | https://github.com/lealoureiro/dvbe26-workshop-exercise-2/issues/1 |
| Functional Specification | issue comment 5670832440 |
| Acceptance Criteria | issue comment 5671347725 |
| Retrieved | 2026-09-14T22:16:20Z |
| Accessible comments | 2 of 2 provider-reported (exhaustively paginated, count reconciled) |
| Derivation direction | issue → OpenSpec (one-way; no synchronization back to the issue) |
| Supplementary repository-owned input | `AGENTS.md` (conventions and approval gates), `oas/my-json-server-oas.yaml` (upstream contract, read-only) |
| Change map | `greek-gods-catalogue-read` → `greek-gods-upstream-sync`; this change is first and has no dependency |
