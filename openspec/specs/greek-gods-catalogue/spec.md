# greek-gods-catalogue Specification

## Purpose
Defines what the service publishes about Greek gods and the guarantees attached to it: the records a consumer receives, the order they arrive in, the uniqueness and permanence of the identifiers in them, and the service's independence from the third-party name source at request time.
## Requirements
### Requirement: Retrieve the Greek gods catalogue

An API consumer SHALL be able to retrieve, in a single request and without authenticating, every Greek god record the service currently holds. Each record SHALL carry a whole-number identifier and a name, and nothing else.

#### Scenario: Catalogue with several records is retrieved

- **WHEN** an API consumer requests the Greek gods and the catalogue holds "Zeus" and "Hera"
- **THEN** the request succeeds
- **AND** the response is JSON
- **AND** the response contains a record for "Zeus" and a record for "Hera"
- **AND** every record carries a whole-number identifier and a name

#### Scenario: Catalogue with a single record is retrieved

- **WHEN** an API consumer requests the Greek gods and the catalogue holds only "Zeus"
- **THEN** the request succeeds
- **AND** the response contains exactly one record
- **AND** that record is for "Zeus"

#### Scenario: No credential is required

- **WHEN** an API consumer requests the Greek gods without presenting any credential
- **THEN** the request succeeds

### Requirement: Return records in ascending identifier order

The service SHALL return records ordered by ascending identifier, and SHALL state that order in its published contract. Because records are only ever added, a consumer that has seen the catalogue before finds earlier records in their previous positions, with later ones appended.

#### Scenario: Records arrive in ascending identifier order

- **WHEN** an API consumer requests the Greek gods and the catalogue holds more than one record
- **THEN** the records appear in ascending order of identifier

#### Scenario: Repeating the request yields the same order

- **WHEN** an API consumer requests the Greek gods twice without the catalogue changing
- **THEN** both responses list the records in the same order

### Requirement: Serve the catalogue independently of the third-party service

The service SHALL answer every catalogue request from its own records. The third-party name source SHALL NOT take part in handling a request, so that neither its latency nor its availability can affect the outcome.

#### Scenario: Third-party service is unavailable

- **WHEN** an API consumer requests the Greek gods while the third-party name source is unavailable
- **THEN** the request succeeds
- **AND** the response contains the records the catalogue holds

#### Scenario: Third-party service is never contacted during a request

- **WHEN** an API consumer requests the Greek gods
- **THEN** no call is made to the third-party name source while the request is handled

### Requirement: Represent an empty catalogue as a successful empty result

An empty catalogue SHALL be treated as a valid state rather than a failure. Before any record exists, the service SHALL report success and return no records.

#### Scenario: Catalogue holds no records

- **WHEN** an API consumer requests the Greek gods and the catalogue is empty
- **THEN** the request succeeds
- **AND** the response contains no records

### Requirement: Publish unique identifiers that the service cannot reassign

An identifier published for a god SHALL denote that same god permanently, and no two records SHALL share an identifier. Identifiers are minted by this service and appear nowhere in the third-party name source. Consumers may persist them.

Permanence is guaranteed structurally: this capability offers no operation that removes a record or changes an identifier, so no consumer of this capability can break the guarantee. Reconciliation is the only writer, and `greek-gods-synchronization` carries the requirements that keep the guarantee under change.

#### Scenario: Identifiers distinguish records

- **WHEN** an API consumer requests the Greek gods and the catalogue holds more than one record
- **THEN** no two records share an identifier

#### Scenario: The published interface offers no way to alter a record

- **WHEN** an API consumer examines the operations this capability publishes
- **THEN** no operation removes a record, changes a name, or changes an identifier

### Requirement: Report a service-side failure distinctly from an empty catalogue

When the service cannot read its catalogue, it SHALL report a failure of its own rather than an empty or partial result, so that a consumer can tell "nothing is recorded" apart from "the request did not work".

#### Scenario: Catalogue cannot be read

- **WHEN** an API consumer requests the Greek gods and the catalogue cannot be read
- **THEN** the consumer is told the failure is the service's own
- **AND** the response does not present an empty catalogue as a success

### Requirement: Publish a contract describing the served behaviour

The service SHALL publish an OpenAPI 3.0.3 contract for the catalogue endpoint, and that contract SHALL describe the responses the service actually serves, including the successful and failure outcomes and the ordering guarantee defined above.

#### Scenario: Contract matches the served response

- **WHEN** an API consumer compares the published contract with a response served by the endpoint
- **THEN** the response conforms to the contract, including its status, media type, and record shape

