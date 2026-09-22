## Purpose

Keeps our own copy of Greek gods up to date by periodically importing names from the upstream Greek gods service in the background, without ever changing the id of a god we already hold.

## ADDED Requirements

### Requirement: Scheduled background sync
The system SHALL fetch the list of names from the upstream service (`GET /greek`, as described in `oas/provided/my-json-server-oas.yaml`) on a schedule, outside of any consumer request. The default interval SHALL be 5 minutes, and the interval SHALL be configurable.

#### Scenario: Default interval
- **GIVEN** no sync interval is configured
- **WHEN** the application is running
- **THEN** the sync runs every 5 minutes

#### Scenario: Configured interval
- **GIVEN** the sync interval is configured to a different value
- **WHEN** the application is running
- **THEN** the sync runs at the configured interval

### Requirement: Unseen names are inserted with a new id
For each upstream name that doesn't match any stored god, the sync SHALL store a new god with that name and a newly assigned id.

#### Scenario: New name
- **GIVEN** no god named "Athena" is stored
- **AND** the upstream service returns `["Zeus", "Athena"]`
- **WHEN** the sync runs
- **THEN** "Athena" is stored with a new id
- **AND** `GET /api/v1/gods` includes "Athena"

### Requirement: Existing gods are never changed
The sync MUST NOT change the id or name of a god that is already stored, and MUST NOT create a second god for a name that is already stored.

#### Scenario: Repeated syncs keep ids stable
- **GIVEN** "Zeus" is stored with id 1
- **AND** the upstream service returns `["Zeus"]`
- **WHEN** the sync runs any number of times
- **THEN** "Zeus" still has id 1
- **AND** exactly one god named "Zeus" is stored

### Requirement: Case-insensitive name matching
The sync SHALL match upstream names against stored names case-insensitively. When a match exists, the stored spelling SHALL be kept. When a single upstream payload contains several case-variants of a new name, the sync SHALL store only the first occurrence.

#### Scenario: Case-variant of an existing name
- **GIVEN** "Zeus" is stored with id 1
- **AND** the upstream service returns `["zeus"]`
- **WHEN** the sync runs
- **THEN** exactly one god matching "zeus" case-insensitively is stored
- **AND** it is named "Zeus" with id 1

#### Scenario: Duplicates within one payload
- **GIVEN** no god named "Athena" is stored
- **AND** the upstream service returns `["Athena", "athena", "Athena"]`
- **WHEN** the sync runs
- **THEN** exactly one god matching "athena" case-insensitively is stored
- **AND** it is named "Athena"

### Requirement: Names missing upstream are kept
The sync MUST NOT delete a stored god whose name no longer appears upstream.

#### Scenario: Name disappears upstream
- **GIVEN** "Zeus" is stored with id 1 and "Hera" with id 2
- **AND** the upstream service returns `["Zeus"]`
- **WHEN** the sync runs
- **THEN** "Hera" is still stored with id 2
- **AND** `GET /api/v1/gods` still includes `{"id":2,"name":"Hera"}`

### Requirement: Failed syncs leave data untouched
When the upstream call fails (HTTP 500, HTTP 504, or a timeout), the sync SHALL log the failure and MUST NOT add, change, or remove any stored god. The next scheduled run SHALL attempt the sync again.

#### Scenario: Upstream returns 500
- **GIVEN** "Zeus" is stored with id 1
- **AND** the upstream service responds with HTTP 500
- **WHEN** the sync runs
- **THEN** the failure is logged
- **AND** the stored gods are unchanged

#### Scenario: Upstream returns 504
- **GIVEN** "Zeus" is stored with id 1
- **AND** the upstream service responds with HTTP 504
- **WHEN** the sync runs
- **THEN** the failure is logged
- **AND** the stored gods are unchanged

#### Scenario: Upstream times out
- **GIVEN** "Zeus" is stored with id 1
- **AND** the upstream service does not respond within the configured timeout
- **WHEN** the sync runs
- **THEN** the failure is logged
- **AND** the stored gods are unchanged

#### Scenario: Recovery on the next run
- **GIVEN** a previous sync failed because upstream was unavailable
- **AND** no god named "Athena" is stored
- **AND** the upstream service now returns `["Zeus", "Athena"]`
- **WHEN** the next scheduled sync runs
- **THEN** "Athena" is stored with a new id
