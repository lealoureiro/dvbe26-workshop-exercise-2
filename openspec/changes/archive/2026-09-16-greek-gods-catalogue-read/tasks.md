## 1. Project scaffolding

- [x] 1.1 Obtain maintainer sign-off for the build, dependency, and schema decisions in this change, as required by the AGENTS.md "Ask first" boundaries
- [x] 1.2 Create `pom.xml` for Java 25 and Spring Boot 4.0.x, with Spring Web, Spring Data/JDBC, Flyway, the PostgreSQL driver, and Testcontainers
- [x] 1.3 Add the Maven wrapper so that `./mvnw` resolves, as `AGENTS.md` documents
- [x] 1.4 Create the Spring Boot application entry point and the `src/main`, `src/test` layout
- [x] 1.5 Configure datasource and Flyway settings for local runs and for tests
- [x] 1.6 Confirm `./mvnw clean verify` passes on an empty project before any behaviour is added, and note that this gate proves the build works rather than that behaviour was preserved (design Risks)

## 2. Catalogue schema

- [x] 2.1 Write the Flyway migration creating `greek_god` with a generated identity `id` and a `name` column
- [x] 2.2 Add the unique constraint on `name` as **case-insensitive** in this same migration, making the natural key structural (design D1). Enabling work for `greek-gods-upstream-sync`: no criterion in this change requires name uniqueness, but the schema is created once, here, so that change ships no migration of its own
- [x] 2.3 Add an integration test asserting the constraint rejects a duplicate name, including one differing only in letter case (enabling work for `greek-gods-upstream-sync`, as in 2.2)
- [x] 2.4 Add an integration test asserting two inserted names receive different identifiers

## 3. Read path

- [x] 3.1 Implement read-only data access returning every catalogue record in ascending identifier order (design D6)
- [x] 3.2 Implement `GET /api/v1/gods/greek` returning records as `{id, name}` JSON, with no other fields, through a controller, a read-only repository, and a `GreekGod` record (design D7, D8)
- [x] 3.3 Ensure the read path holds no client for the third-party service (design D3)
- [x] 3.4 Map a catalogue read failure to a service-side failure response, distinct from an empty result
- [x] 3.5 Add unit tests for the response mapping, including the empty-catalogue case
- [x] 3.6 Confirm this capability publishes no operation that removes a record, changes a name, or changes an identifier

## 4. Published contract

- [x] 4.1 Author `oas/greekController-oas.yaml` as OpenAPI 3.0.3 describing the endpoint, the record shape, and the success and failure outcomes
- [x] 4.2 State the ascending-identifier ordering guarantee in the contract description (design D6)
- [x] 4.3 Add a test asserting a served response conforms to the published contract, so drift fails the build (design D4)

## 5. Acceptance coverage

- [x] 5.1 Set up Testcontainers-backed acceptance tests against a real PostgreSQL instance (design D5)
- [x] 5.2 Cover "Catalogue with several records is retrieved": populated catalogue returns every record with identifier and name
- [x] 5.3 Cover "Catalogue holds no records": empty catalogue returns success with no records
- [x] 5.4 Cover "Third-party service is unavailable": the request still succeeds with the third-party service unreachable
- [x] 5.5 Cover "Identifiers distinguish records": no two records share an identifier
- [x] 5.6 Cover "Catalogue with a single record is retrieved": exactly one record is returned
- [x] 5.7 Cover "Records arrive in ascending identifier order"
- [x] 5.8 Cover "Repeating the request yields the same order": two consecutive requests list records identically
- [x] 5.9 Cover "The published interface offers no way to alter a record"
- [x] 5.10 Cover "Catalogue cannot be read": a service-side failure is reported, and is distinguishable from an empty catalogue
- [x] 5.11 Cover "No credential is required": the request succeeds with no credential presented

## 6. Verification

- [x] 6.1 Run `./mvnw clean verify` and confirm unit, integration, and acceptance tests pass
- [x] 6.2 Confirm every requirement in `specs/greek-gods-catalogue/spec.md` has covering test coverage
- [x] 6.3 Confirm no request path reaches the third-party service, by inspection and by the test in 5.4
- [x] 6.4 Confirm each of the 12 scenarios in `specs/greek-gods-catalogue/spec.md` maps to at least one task above
