## Context

`greek-gods-catalogue-read` leaves the catalogue with a unique constraint on `name`, a database-generated identity column, and no mutation path. This change adds the only writer that catalogue will have.

See `proposal.md` — Why, for motivation, and `specs/greek-gods-synchronization/spec.md` for the requirements.

The third party supplies content; this service owns identity. Reconciliation is the seam between the two, and therefore the only place the identity guarantee can be broken. `feature1.md` states the rule plainly: a name never seen is inserted, a name already held is left alone.

Three of the four questions this design originally deferred — U-1, U-2 and A-7 — were decided during design exploration and are recorded as D6, D7 and D8 below. U-3 remains open; it is empirical and cannot be settled by decision, only by observing the source over time. It does not block the approach, the specs, or the task breakdown.

## Goals / Non-Goals

**Goals:**

- A reconciliation whose effect on identifiers is provably nil for names already held.
- Convergence under repetition: running it twice changes nothing the first run did not.
- Failure containment: an upstream outage costs freshness and nothing else.

**Non-Goals:**

- Changing anything about what a consumer receives. `greek-gods-catalogue` keeps every requirement it has.
- Removing, renaming, or merging records. Insert-only is decision D-1.
- Any request-time interaction with the third party. That is forbidden by the read capability and is not softened here.
- Deciding cadence or triggering — see Open Questions.

## Decisions

**D1 — Reconcile name by name, not by replacing the catalogue.**
The insert-only rule is a property of each name considered individually. Any wholesale approach — truncate and reload, delete-then-insert, or a diff that deletes what the third party no longer offers — reassigns or destroys identifiers as a side effect, which is exactly the failure identified as the root cause. Per-name reconciliation makes the rule structural rather than a matter of ordering care.

**D2 — Let the database's unique constraint on `name` decide what is new.**
Rather than reading the catalogue, comparing in memory, and then inserting — which leaves a window where a concurrent run inserts the same name — the insert itself is written to do nothing when the name is already present. The constraint established in the read change is the arbiter, so a concurrent run is a no-op instead of a conflict, and assumption A-5 stops being load-bearing.

**D3 — Case-insensitive matching lives in the constraint, and is created by the read change.**
The specification requires "zeus" to resolve to a stored "Zeus". Doing this in application code would reopen the concurrency window D2 closes, so case-insensitive matching belongs in the same constraint that enforces uniqueness. That constraint is created case-insensitive by `greek-gods-catalogue-read`, in the migration that creates the table, so **this change ships no migration at all**.
Parallel change — expand, migrate, contract — was considered and rejected. It exists for when old and new application versions or data interpretations must coexist, and neither does here: this change is the catalogue's first and only writer, so the table is necessarily empty until it exists. With no data to reinterpret and no deployed reader relying on the old constraint semantics, a compatibility window would protect nothing. Altering the constraint in a second migration was likewise rejected: neither change is implemented yet, so the churn buys nothing and would need a guard against case-variant rows that cannot exist.

**D4 — Treat the upstream payload as untrusted input.**
Names arrive from a public mock with no SLA and no change notification. They are validated for emptiness and trimmed of surrounding whitespace before matching, because assumption A-1 — that names are byte-stable — is unverified, and a padded name would otherwise create a second record for one god, breaking the identity guarantee through the back door rather than by reassignment.

**D5 — Record the outcome of every run.**
A background process that fails silently presents as correct behaviour: the endpoint keeps answering while the catalogue quietly goes stale. Since no freshness bound exists to alarm on (U-1), the minimum is that a run's outcome is observable after the fact.

**D6 — Hourly cadence, configurable, with the staleness bound stated as a requirement.**
A name offered upstream appears within one cadence period. The dataset is mythology behind a free public mock and is effectively static, so a tighter loop would add upstream load without adding freshness — and upstream pressure is exactly what the quality attributes caution against. Configurable so a workshop session can shorten it to demonstrate the behaviour. A daily cadence was considered and rejected only for making the feedback loop awkward to observe.

**D7 — A schedule inside the API application, plus one run at start-up.**
One deployable, matching the scope of the exercise. The start-up run exists because decision D-2 makes an empty catalogue valid but not desirable: without it, a freshly started service serves an empty list for up to a full cadence period. A separate deployable was rejected as doubling the deployment units for a project with no deployment environment; a manual-only trigger was rejected because the catalogue would then reflect only what someone remembered to run.
Running in-process means two instances could reconcile at once. That is already handled: D2 makes a concurrent insert a no-op rather than a conflict, so assumption A-5 is not load-bearing.

**D8 — Commit per name, not per run.**
Insert-only means a record already written is correct on its own, independent of whatever the rest of the run does. A partial run therefore leaves a correct-but-incomplete catalogue that the next run completes, and the system converges. A run-wide transaction was rejected: it would hold a transaction open across every insert and a network call, and would discard correct work in exchange for an atomicity nothing here needs. No identifier is ever at risk either way.

**D9 — A permanent operational kill switch, defaulting to on and failing safe to off.**
The reconciliation reaches a third party with no SLA on a schedule. An operator needs to stop that without a redeployment — a classic operational kill switch rather than a release toggle, so it is permanently owned and has no removal trigger. It defaults to on because reads never depend on reconciliation, so leaving it on is safe; and an unreadable setting is treated as off, so a configuration failure can never produce unintended calls to the third party. Evaluation sits at the scheduled entry point, one decision per run, rather than as flag checks scattered through the reconciliation logic.
This switch does **not** stand in for the trigger decision: D7 settles that independently. A toggle used to avoid deciding would be exactly the misuse that feature-toggle guidance warns against.

## Risks / Trade-offs

- **An upstream rename beyond letter case yields a second identifier for one god** (U-3) → Not mitigated. Insert-only plus case-insensitive matching covers every case except a genuine spelling correction, which reads as one removal and one addition. Detection needs two upstream snapshots compared over time.
- **An hourly cadence is a judgement, not a measured requirement** → It is now stated as a requirement with an observable staleness bound, so it can be tested and revisited deliberately. No upstream change frequency was measured, because the source offers no way to observe one.
- **A run failing midway leaves a partial result** → Decided and specified (D8). Harmless for identity, and the next run completes the remainder; the risk that remains is a catalogue briefly incomplete, bounded by one cadence period.
- **A stopped kill switch is indistinguishable from a healthy quiet system** → The catalogue goes stale silently while reads keep succeeding, which is the failure mode quality attribute 6 identifies. Mitigated only by D5's run-outcome recording; no alerting exists, since there is no environment to alert in.
- **The third party disappearing entirely** → Contained by design: the catalogue stays readable and keeps every record. The cost is frozen content, not an outage.

## Migration Plan

No migration. The constraint this change relies on is created by `greek-gods-catalogue-read` (D3). Rollback is the kill switch: stopping reconciliation leaves the catalogue holding everything it has and the read path untouched, with no redeployment and no data loss. Nothing this change does is destructive, which is a direct consequence of insert-only.

## Open Questions

One question remains, and it is genuinely deferrable — it changes neither the specs, the approach, nor the task breakdown:

- **U-3 — Whether the third party ever corrects a spelling beyond letter case.** If it does, that rename reads as one removal plus one addition under a name-based key, producing a second identifier for one god. Insert-only and case-insensitive matching cover every other case. It cannot be decided, only observed: task 1.1 compares two upstream snapshots over time, and task 6.4 records the residual risk rather than closing it if no rename is seen.
