## 1. Upstream investigation

- [x] 1.1 Investigate U-3: fetch the upstream payload twice, separated in time, and compare the name sets for corrected spellings beyond letter case
- [x] 1.2 Validate assumptions A-1, A-2 and unknown U-5 in the same fetch: record the exact strings, check for case and whitespace variation and duplicates, and count the entries
- [ ] 1.3 Record what was observed on issue #1, so the assumptions behind the matching rules are evidenced rather than assumed

## 2. Third-party source client

- [x] 2.1 Implement an outbound client reading the name list, per `oas/my-json-server-oas.yaml`
- [x] 2.2 Apply timeouts so a slow or hanging source cannot stall a run indefinitely
- [x] 2.3 Trim surrounding whitespace from each received name before matching, and skip names that are empty or whitespace-only without ending the run (design D4)
- [x] 2.4 Add tests against a stubbed source covering a normal payload, an empty list, a timeout, and a `500` and `504` response

## 3. Reconciliation

- [x] 3.1 Implement per-name reconciliation that takes up an unseen name and leaves a held name untouched (design D1)
- [x] 3.2 Write the insert so that the case-insensitive unique constraint decides what is new, doing nothing when the name is already present (design D2, D3)
- [x] 3.3 Commit each name independently rather than wrapping a run in one transaction (design D8)
- [x] 3.4 Confirm no removal or identifier-reassignment path exists anywhere in the component
- [x] 3.5 Record the outcome of every run, including failures, in a form an operator can observe (design D5)
- [x] 3.6 Confirm this change adds no Flyway migration; the constraint it relies on is created by `greek-gods-catalogue-read` (design D3)

## 4. Scheduling and operational control

- [x] 4.1 Run reconciliation on a configurable fixed delay, defaulting to one hour (design D6)
- [x] 4.2 Run reconciliation once at start-up so a cold catalogue does not wait a full cadence period (design D7)
- [x] 4.3 Implement the kill switch as a single typed decision evaluated at the scheduled entry point, not as flag checks inside the reconciliation logic (design D9)
- [x] 4.4 Default the kill switch to on, and treat an unreadable setting as off so a configuration failure cannot cause unintended calls to the third-party source (design D9)
- [x] 4.5 Drive time from an injectable clock and a controllable scheduler, so cadence behaviour is testable without waiting

## 5. Acceptance coverage

- [x] 5.1 Cover "An unseen name is taken up": an absent name becomes retrievable with an identifier of its own
- [x] 5.2 Cover "Only the unseen names are taken up": held names keep their identifiers while a new one is added
- [x] 5.3 Cover "A name already held is offered again": the identifier is unchanged
- [x] 5.4 Cover "Repeating reconciliation changes nothing": two runs over an unchanged source leave every identifier and the record count untouched
- [x] 5.5 Cover "An offered name disappears": the record and its identifier survive
- [x] 5.6 Cover "A case variant of a stored name is offered": one record, identifier unchanged
- [x] 5.7 Cover "The third-party source is unavailable during a run": the catalogue is unchanged and still readable
- [x] 5.8 Cover "A failed run is observable": the failure is recorded and the request path is unaffected
- [x] 5.9 Cover "A newly offered name appears within one cadence period", using the controllable clock from 4.5 rather than waiting
- [x] 5.10 Cover "A run happens at start-up": a missing name is taken up without a full cadence period elapsing
- [x] 5.11 Cover "A run fails partway through": names already taken up remain, with their identifiers
- [x] 5.12 Cover "A later run completes what a failed run left": the catalogue ends complete, earlier identifiers unchanged
- [x] 5.13 Cover "Reconciliation is stopped": no call to the third-party source, catalogue unchanged, reads still succeed
- [x] 5.14 Cover "Reconciliation is started again": due names are taken up once more
- [x] 5.15 Cover "The control cannot be read": treated as stopped, no call made, reads still succeed
- [x] 5.16 Cover "A configured cadence replaces the default", using the controllable clock from 4.5
- [x] 5.17 Cover "A padded form of a stored name is offered": one record, identifier unchanged
- [x] 5.18 Cover "An empty name is offered alongside valid ones": the empty name is skipped and the run continues

## 6. Verification

- [x] 6.1 Run `./mvnw clean verify` and confirm unit, integration, and acceptance tests pass
- [x] 6.2 Confirm every requirement in `specs/greek-gods-synchronization/spec.md` has covering test coverage
- [x] 6.3 Confirm the read capability's requirements still hold unchanged after reconciliation runs, including ascending identifier order
- [ ] 6.4 Record the residual U-3 rename risk on issue #1 if 1.1 observed no rename, rather than closing it
- [x] 6.5 Confirm each of the 18 scenarios in `specs/greek-gods-synchronization/spec.md` maps to at least one task above
