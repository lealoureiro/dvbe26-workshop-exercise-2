## 1. Project bootstrap

- [ ] 1.1 Create Spring Boot 4 / Java 25 Maven project with Maven Wrapper (`./mvnw`) and application class
- [ ] 1.2 Add approved dependencies: Spring Web MVC, Spring Data JDBC, PostgreSQL driver, Testcontainers (PostgreSQL), WireMock
- [ ] 1.3 Verify `./mvnw clean verify` passes on the empty application with a Testcontainers-backed context-load test

## 2. Persistence

- [ ] 2.1 Add `schema.sql` creating `god` (BIGINT identity id, name) and unique index on `lower(name)`; enable `spring.sql.init.mode=always`
- [ ] 2.2 Add `God` aggregate and Spring Data JDBC repository with ordered find-all and insert-if-absent (`ON CONFLICT (lower(name)) DO NOTHING`) query
- [ ] 2.3 Integration test: insert-if-absent keeps existing id and spelling for exact and case-variant names

## 3. Read endpoint

- [ ] 3.1 Implement `GET /api/v1/gods` returning a plain JSON array of `{id, name}` ordered by id
- [ ] 3.2 Enable Problem Details and map data-access failures to HTTP 500 `application/problem+json`
- [ ] 3.3 Integration tests: stored gods listed, empty list, anonymous access, no extra fields
- [ ] 3.4 Integration test: database unavailable → 500 Problem Details with no god list
- [ ] 3.5 Integration test: upstream (WireMock) failing/slow → read returns 200 and WireMock receives zero requests

## 4. Upstream client

- [ ] 4.1 Add validated `@ConfigurationProperties` for upstream base URL (default from OAS), connect timeout, and read timeout
- [ ] 4.2 Implement `RestClient`-based client for `GET /greek` returning a list of names

## 5. Sync job

- [ ] 5.1 Implement sync service: fetch names, dedupe case-insensitively keeping first occurrence, insert-if-absent in one transaction
- [ ] 5.2 Catch and log upstream failures (5xx, timeout, unparseable body) without writing to the database
- [ ] 5.3 Schedule sync with `fixedDelay` from `greek-gods.sync.interval` (default `PT5M`) and enable scheduling
- [ ] 5.4 Unit test: in-memory dedupe keeps first occurrence per lower-cased name
- [ ] 5.5 Integration tests (WireMock + Testcontainers): new name inserted; repeated syncs keep ids; case-variant keeps stored spelling; in-payload duplicates stored once; removed names kept
- [ ] 5.6 Integration tests: upstream 500, 504, and timeout log failure and leave data unchanged; next run after failure inserts new names
- [ ] 5.7 Test: sync interval binds to 5 minutes by default and to an overridden value

## 6. Verification

- [ ] 6.1 Run `./mvnw clean verify` and confirm all tests pass
- [ ] 6.2 Confirm every scenario in `specs/greek-gods-read-api` and `specs/greek-gods-sync` is covered by at least one test
