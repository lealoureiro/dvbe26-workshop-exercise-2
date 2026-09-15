## Purpose

Defines how names held by the third-party source become records in the Greek gods catalogue, how promptly they arrive, and what reconciliation guarantees about the identifiers already published: that it only ever adds, that repeating it changes nothing, that a failure leaves the catalogue intact, and that an operator can stop it.

## ADDED Requirements

### Requirement: Take up names the catalogue has not seen

Reconciliation SHALL bring each name held by the third-party source but absent from the catalogue into the catalogue, publishing it with an identifier of its own.

#### Scenario: An unseen name is taken up

- **WHEN** the third-party source offers "Poseidon", the catalogue does not hold it, and reconciliation runs
- **THEN** a later request for the Greek gods contains a record for "Poseidon"
- **AND** "Poseidon" is published with an identifier no other record holds

#### Scenario: Only the unseen names are taken up

- **WHEN** the third-party source offers "Zeus", "Hera" and "Poseidon", the catalogue already holds "Zeus" and "Hera", and reconciliation runs
- **THEN** "Zeus" and "Hera" keep the identifiers they were already published with
- **AND** a record for "Poseidon" is published with an identifier of its own

### Requirement: Never reassign a published identifier

Reconciliation SHALL leave the identifier of every existing record untouched. A name already in the catalogue SHALL NOT cause a new record, a changed identifier, or a removal.

#### Scenario: A name already held is offered again

- **WHEN** the catalogue holds "Zeus", an API consumer has noted its published identifier, the third-party source offers "Zeus", and reconciliation runs
- **THEN** "Zeus" keeps the identifier the consumer noted

#### Scenario: Repeating reconciliation changes nothing

- **WHEN** the catalogue holds "Zeus" and "Hera" and reconciliation runs twice with the third-party source unchanged
- **THEN** each god keeps the identifier it was published with
- **AND** the catalogue holds no further records

### Requirement: Keep records the third-party source no longer offers

Reconciliation SHALL only ever add. A name that disappears from the third-party source SHALL leave its record and identifier in place, so that an identifier can never be recycled and handed to a different god.

#### Scenario: An offered name disappears

- **WHEN** the catalogue holds "Zeus", an API consumer has noted its published identifier, the third-party source stops offering "Zeus", and reconciliation runs
- **THEN** a later request for the Greek gods still contains a record for "Zeus"
- **AND** "Zeus" keeps the identifier the consumer noted

### Requirement: Recognise a name differing only in letter case as the same god

Reconciliation SHALL treat a name that differs from a stored name only in letter case as that same god, keeping the existing record rather than publishing a second one.

#### Scenario: A case variant of a stored name is offered

- **WHEN** the catalogue holds "Zeus", an API consumer has noted its published identifier, the third-party source offers "zeus", and reconciliation runs
- **THEN** the catalogue holds one record for that god
- **AND** it keeps the identifier the consumer noted

### Requirement: Leave the catalogue intact when the third-party source fails

Reconciliation SHALL run out-of-band with any request. When the third-party source is unavailable, times out, or fails, the catalogue SHALL remain readable and unchanged.

#### Scenario: The third-party source is unavailable during a run

- **WHEN** reconciliation runs and the third-party source is unavailable
- **THEN** the catalogue still holds the records it held before the run
- **AND** a request for the Greek gods still succeeds

#### Scenario: A failed run is observable

- **WHEN** reconciliation runs and the third-party source fails
- **THEN** the failure is recorded in a form an operator can observe
- **AND** the request path is unaffected

### Requirement: Reconcile repeatedly without operator intervention

Reconciliation SHALL run on a recurring schedule and once when the service starts, so that a name offered by the third-party source appears in the catalogue within one cadence period without anyone triggering a run. The cadence SHALL be configurable, and SHALL default to one hour.

#### Scenario: A newly offered name appears within one cadence period

- **WHEN** the third-party source begins offering "Poseidon" and one cadence period elapses
- **THEN** a request for the Greek gods contains a record for "Poseidon"

#### Scenario: A run happens at start-up

- **WHEN** the service starts with a catalogue that is missing a name the third-party source offers
- **THEN** that name is taken up without waiting for a full cadence period

#### Scenario: A configured cadence replaces the default

- **WHEN** an operator configures a cadence other than the default and that period elapses
- **THEN** a name newly offered by the third-party source appears in the catalogue
- **AND** runs follow the configured cadence rather than the default

### Requirement: Keep names taken up before a run fails

Reconciliation SHALL take up each name independently. When a run fails partway, the names already taken up SHALL remain in the catalogue with their identifiers, and a later run SHALL take up the remainder.

#### Scenario: A run fails partway through

- **WHEN** the third-party source offers "Zeus", "Hera" and "Poseidon", and reconciliation fails after taking up "Zeus" and "Hera"
- **THEN** a request for the Greek gods contains records for "Zeus" and "Hera"
- **AND** they hold the identifiers they were published with

#### Scenario: A later run completes what a failed run left

- **WHEN** a run has failed after taking up only some of the offered names, and a later run completes
- **THEN** a request for the Greek gods contains a record for every name the third-party source offers
- **AND** the names taken up by the earlier run keep their identifiers

### Requirement: Let an operator stop reconciliation

An operator SHALL be able to stop reconciliation without redeploying the service. While it is stopped, no call SHALL be made to the third-party source and the catalogue SHALL keep every record it holds. Reads are unaffected either way. When the control cannot be read, reconciliation SHALL be treated as stopped, so that an unreadable setting never causes unintended calls to the third-party source.

#### Scenario: Reconciliation is stopped

- **WHEN** an operator stops reconciliation and a run would otherwise have been due
- **THEN** no call is made to the third-party source
- **AND** the catalogue still holds every record it held
- **AND** a request for the Greek gods still succeeds

#### Scenario: Reconciliation is started again

- **WHEN** an operator starts reconciliation again and a run becomes due
- **THEN** names offered by the third-party source and absent from the catalogue are taken up

#### Scenario: The control cannot be read

- **WHEN** the setting governing reconciliation cannot be read and a run would otherwise have been due
- **THEN** no call is made to the third-party source
- **AND** a request for the Greek gods still succeeds

### Requirement: Take up only names that conform to the expected shape

A name offered by the third-party source SHALL be matched disregarding surrounding whitespace, so that a padded form of a stored name resolves to that stored record rather than creating a second one. A name that is empty, or consists only of whitespace, SHALL be skipped without being taken up and without ending the run.

#### Scenario: A padded form of a stored name is offered

- **WHEN** the catalogue holds "Zeus", an API consumer has noted its published identifier, the third-party source offers " Zeus " with surrounding whitespace, and reconciliation runs
- **THEN** the catalogue holds one record for that god
- **AND** it keeps the identifier the consumer noted

#### Scenario: An empty name is offered alongside valid ones

- **WHEN** the third-party source offers an empty name together with "Hera", and reconciliation runs
- **THEN** no record is created for the empty name
- **AND** a record for "Hera" is published with an identifier of its own
