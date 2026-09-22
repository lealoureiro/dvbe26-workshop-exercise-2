## Context

The repository is greenfield: there is no `pom.xml` and no application code yet. The upstream contract (`oas/provided/my-json-server-oas.yaml`) is fixed and read-only: `GET /greek` returns `string[]` and may respond `500` or `504`. The mandated stack (`AGENTS.md`) is Java 25, Spring Boot 4, Maven Wrapper and PostgreSQL. See `proposal.md` for motivation and the specs for the required behavior.

## Goals / Non-Goals

**Goals:**
- The read path touches only PostgreSQL; the sync path is the only code that talks to upstream.
- Id stability and case-insensitive uniqueness are enforced by the database, not only by application logic.
- Behavior is covered by integration tests against a real PostgreSQL instance and a stubbed upstream.

**Non-Goals:**
- Authentication or authorization.
- Pagination, filtering or sorting parameters on the read endpoint.
- Caching in front of the database.
- Deleting or renaming gods.
- Retries within a single sync run (the next scheduled run is the retry).
- Numeric latency or availability targets (none were set).

## Decisions

### D1. Two paths sharing one table
A read slice (controller → repository) and a sync slice (scheduler → upstream client → repository) share only the gods table. The controller has no dependency on the upstream client, which keeps the read endpoint isolated from upstream by construction.
- *Alternative:* read-through cache that calls upstream on a miss. Rejected because it breaks isolation from upstream.

### D2. Schema via Spring SQL initialization (`schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS god (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS god_name_lower_uq ON god (lower(name));
```
- `spring.sql.init.mode=always` runs the script on startup. The script is idempotent.
- `GENERATED ALWAYS AS IDENTITY` stops callers from supplying or overwriting ids.
- The unique index on `lower(name)` enforces case-insensitive uniqueness.
- *Alternative:* Flyway, which gives versioned migrations. Not approved as a dependency for this change.
- *Alternative:* `CITEXT`, which needs a PostgreSQL extension. The functional index is simpler.

### D3. Insert-only sync using `ON CONFLICT DO NOTHING`
Each run works like this:
1. Fetch the names from upstream.
2. Dedupe them in memory, keeping the first occurrence per `name.toLowerCase(Locale.ROOT)` in a `LinkedHashMap`.
3. Run `INSERT INTO god (name) VALUES (?) ON CONFLICT (lower(name)) DO NOTHING` for each name, in one transaction.

Existing rows are never updated or deleted, which covers stable ids, keeping the stored spelling, and keeping names that disappear upstream.
- *Alternative:* read all rows and diff in Java. This is racy without extra locking and adds code.
- *Alternative:* `MERGE`. It offers no benefit over `ON CONFLICT DO NOTHING` here.

### D4. Fetch before write; failures abort before touching the database
The upstream call completes, and its response is fully parsed, before any write starts. On failure the scheduler catches the exception, logs it at WARN/ERROR with the cause, and returns. Failures include HTTP 5xx, connect/read timeout, and an unparseable body. Nothing is written, and the next scheduled run retries.

### D5. Scheduling
Scheduling uses `@Scheduled(fixedDelayString = "${greek-gods.sync.interval:PT5M}")` with `@EnableScheduling`. A fixed delay stops slow upstream calls from overlapping.
- *Alternative:* `fixedRate`, which could stack up runs when upstream is slow.

### D6. Upstream client
The upstream client is a Spring `RestClient` configured from these properties:
- `greek-gods.upstream.base-url`, defaulting to the OAS server URL
- `greek-gods.upstream.connect-timeout`
- `greek-gods.upstream.read-timeout`

These are bound through a validated `@ConfigurationProperties` record. Tests point `base-url` at WireMock.

### D7. Persistence
Spring Data JDBC maps the `God` aggregate (`id`, `name`) and serves reads. The insert-only upsert in D3 is a custom `@Modifying @Query`, because Spring Data JDBC's `save` doesn't express `ON CONFLICT`.

### D8. Read endpoint and error handling
- `GET /api/v1/gods` returns `List<GodResponse>`, a record with `long id` and `String name`, serialized as a plain JSON array ordered by `id`.
- `spring.mvc.problemdetails.enabled=true` produces RFC 9457 bodies. A `@RestControllerAdvice` maps `DataAccessException` to 500 `ProblemDetail`, so database failures return `application/problem+json` without internal details.

### D9. Testing
- **Integration tests** use `@SpringBootTest` with Testcontainers PostgreSQL (`@ServiceConnection`) and WireMock for upstream. They cover every scenario in both specs.
- **Unit tests** cover the in-memory dedupe logic.
- **Isolation test:** WireMock is stubbed to fail or delay, then the test asserts that the read endpoint returns 200 and that WireMock received zero requests.
- **Scheduling test:** asserts that the interval property binds, both at its default and when overridden. Tests don't wait on real 5-minute timers; they invoke the sync service directly.

## Risks / Trade-offs

- **[Risk]** `schema.sql` has no versioning, so later schema changes need manual care. → **Mitigation:** keep it idempotent. Revisit Flyway if the schema evolves (it would need dependency approval).
- **[Risk]** Java `toLowerCase(Locale.ROOT)` and PostgreSQL `lower()` could disagree for some non-ASCII characters. → **Mitigation:** upstream data is ASCII god names. If they ever disagree, the database index stays authoritative and `ON CONFLICT` still prevents duplicates.
- **[Risk]** The first sync runs at startup, so if upstream is down then the list stays empty until a later run succeeds. → **Mitigation:** this is accepted by the spec. The read endpoint still returns 200 with an empty array.
- **[Trade-off]** Running the sync on several application instances would perform duplicate upstream calls. This is harmless because inserts are idempotent. No distributed lock is added.

## Migration Plan

This is a greenfield deployment. On startup, `schema.sql` creates the table and index if they're missing. Rollback means removing the deployment. The table can be dropped safely because no other system owns it.

## Open Questions

- Exact default values for the upstream connect and read timeouts. These can be set during implementation without changing the specs or tasks.
