## Purpose

Lets API consumers retrieve the list of Greek gods, each with a stable id and a name, from a public endpoint that depends only on our own database and never on the upstream service.

## ADDED Requirements

### Requirement: List Greek gods
The system SHALL expose `GET /api/v1/gods`, which returns HTTP 200 with a JSON array containing every stored god. Each element SHALL contain exactly two fields: `id` (number) and `name` (string).

#### Scenario: Stored gods are listed
- **GIVEN** the stored gods are "Zeus" with id 1 and "Hera" with id 2
- **WHEN** a consumer sends `GET /api/v1/gods`
- **THEN** the response status is 200
- **AND** the body is a JSON array containing `{"id":1,"name":"Zeus"}` and `{"id":2,"name":"Hera"}`
- **AND** no element contains fields other than `id` and `name`

#### Scenario: No gods stored
- **GIVEN** no gods are stored
- **WHEN** a consumer sends `GET /api/v1/gods`
- **THEN** the response status is 200
- **AND** the body is an empty JSON array

### Requirement: Public access
The read endpoint SHALL be accessible without authentication.

#### Scenario: Anonymous consumer
- **WHEN** a consumer sends `GET /api/v1/gods` without any credentials
- **THEN** the response status is 200

### Requirement: Isolation from the upstream service
Serving the read endpoint MUST NOT call any external service, and MUST NOT trigger a sync. Its availability and latency SHALL be independent of the upstream Greek gods service.

#### Scenario: Upstream is down or slow
- **GIVEN** the stored gods include "Zeus" with id 1
- **AND** the upstream Greek gods service is unavailable or responding slowly
- **WHEN** a consumer sends `GET /api/v1/gods`
- **THEN** the response status is 200
- **AND** the body contains `{"id":1,"name":"Zeus"}`
- **AND** no request is sent to the upstream service

#### Scenario: Request does not trigger a sync
- **WHEN** a consumer sends `GET /api/v1/gods`
- **THEN** no request is sent to the upstream service

### Requirement: Server-side failures are reported, not hidden
When the system cannot serve the list (for example, the database is unavailable), the endpoint SHALL respond with HTTP 500 and an RFC 9457 Problem Details body (`application/problem+json`). It MUST NOT return partial or stale in-memory data.

#### Scenario: Database unavailable
- **GIVEN** the database is unavailable
- **WHEN** a consumer sends `GET /api/v1/gods`
- **THEN** the response status is 500
- **AND** the content type is `application/problem+json`
- **AND** the body does not contain a list of gods
