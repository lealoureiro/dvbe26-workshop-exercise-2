## Why

`greek-gods-catalogue-read` establishes a catalogue and the guarantees attached to it, but nothing fills it — the endpoint serves an empty result until names arrive. The names exist only in a third-party service that supplies no identifiers, so bringing them in is also the moment identity is assigned, and the moment it could be broken.

## What Changes

- Introduce a background reconciliation that reads the third-party name catalogue and brings unseen names into the local catalogue, out-of-band with any request.
- Assign an identifier to each newly taken-up name, and leave the identifier of an existing record untouched.
- Take up names only: a record is never removed, and never has its identifier reassigned (**decision D-1**, insert-only).
- Recognise a name differing from a stored one only in letter case as the same god, keeping the existing record and its identifier.
- Leave the catalogue readable and unchanged when the third-party service is unavailable or fails.
- Run reconciliation on a recurring schedule inside the API application, plus once at start-up, with an hourly default cadence.
- Take up each name independently, so a run that fails partway keeps what it already added.
- Match names disregarding surrounding whitespace, and skip empty names without ending the run.
- Provide a permanent operational kill switch that stops reconciliation without a redeployment.

No breaking change: the read contract established by `greek-gods-catalogue-read` is unaffected. Consumers see the same record shape, with more records in it.

## Capabilities

### New Capabilities

- `greek-gods-synchronization`: how names from the third-party source become catalogue records, and what reconciliation guarantees about identifiers — that it only ever adds, that repetition changes nothing, and that upstream failure leaves the catalogue intact.

### Modified Capabilities

None. `greek-gods-catalogue` keeps every requirement it has: what a consumer receives and the guarantees on it are unchanged by how records arrive.

## Impact

- **New:** the reconciliation component, an outbound client for the third-party service, its schedule, its kill switch, and their configuration.
- **No schema migration.** The case-insensitive unique constraint this change depends on is created by `greek-gods-catalogue-read` in the migration that creates the table.
- **Depends on:** `greek-gods-catalogue-read` for the `greek_god` schema, the case-insensitive unique constraint on `name`, and the identifier guarantees this change must not violate.
- **New external dependency at runtime:** `https://my-json-server.typicode.com/jabrena/latency-problems/greek`, a public mock with no SLA, no authentication, and no change notification, whose own contract advertises `500` and `504`. It is reached only out-of-band, never while a request is handled.
- **Consumers:** unaffected in shape; the catalogue they read stops being empty.

## Decided during design exploration

| Ref | Question | Decision |
| --- | --- | --- |
| U-1 | Cadence and acceptable staleness | Hourly by default and configurable. A name offered upstream appears in the catalogue within one cadence period. The dataset is effectively static, so a faster poll would add upstream load without adding freshness |
| U-2 | What triggers a run | A schedule inside the API application, plus one run at start-up so a cold catalogue fills promptly. One deployable |
| A-7 | Whether a partial run may keep its additions | Yes. Each name is taken up independently; insert-only makes every record already written correct on its own, and the next run completes the rest |

## Unresolved

| Ref | Question | Consequence of leaving it open |
| --- | --- | --- |
| U-3 | Whether the third party ever corrects a spelling beyond letter case | A rename under a name-based key reads as one removal plus one addition, producing a second identifier for one god — the one case insert-only does not cover. Empirical; it cannot be settled by decision, only by observing the source over time |

## Traceability

| Field | Value |
| --- | --- |
| Source issue | https://github.com/lealoureiro/dvbe26-workshop-exercise-2/issues/1 |
| Functional Specification | issue comment 5670832440 |
| Acceptance Criteria | issue comment 5671347725 |
| Retrieved | 2026-09-14T22:16:20Z |
| Accessible comments | 2 of 2 provider-reported (exhaustively paginated, count reconciled) |
| Derivation direction | issue → OpenSpec (one-way; no synchronization back to the issue) |
| Supplementary repository-owned input | `AGENTS.md`, `oas/my-json-server-oas.yaml` (upstream contract, read-only) |
| Change map | `greek-gods-catalogue-read` → `greek-gods-upstream-sync`; this change is second and depends on the first |
