# Checkpoint 05 — Final Validation

## Entry Gate

Proceed only if Checkpoints 01–04 are `COMPLETE` and the controller marks this file `IN_PROGRESS`. This is the final checkpoint.

## Scope

Validate the integrated result against quality, performance, correctness, migration, security, and UX contracts. Fix only release-blocking regressions found by these checks; do not add features or redesign completed checkpoints.

## Relevant Code Areas

Review changed files and tests from prior checkpoints, plus:

- `docs/adr/0004-retries-and-leases.md`
- `docs/adr/0005-external-endpoints-and-adapters.md`
- `docs/RELEASE/_READINESS.md`
- `PROGRESS.md`
- Gradle/Room schemas and test reports

## Required Outcomes

Produce current, reproducible evidence for:

- quality corpus routing, including preservation of valuable short comments, criticism, disagreement, negative sentiment, and minority opinions;
- rejection of trash, exact duplicates, spam, toxic-only, incoherent, irrelevant, boilerplate, and non-contributing comments;
- mandatory JEV before GLM and no GLM/synthesis use of rejected/manual-review comments;
- item-level JEV partial success;
- JEV classification authority and GLM summary-only schema;
- adaptive batching, bounded concurrency, split behavior, cancellation, and shared HTTP safety;
- retry timing, lease recovery, idempotency, process death, and >5,000-item completion;
- migration/history compatibility, Room query efficiency, synthesis bounds, and UI terminal states;
- baseline-versus-final request count, p50/p95, first-result time, total duration, tokens, cost, retries, and throughput.

Update ADR/readiness/progress documents to match verified code only. Resolve contradictions between old readiness records and current evidence.

## Acceptance Checks

Run and record, as supported by the repository/environment:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

Also verify:

- no secret/full-sensitive-body leakage in logs or persisted telemetry;
- consent is required before remote processing;
- strict schemas and exact IDs reject malformed/mismatched output;
- all supported Room migrations pass without destructive fallback;
- cancellation leaves recoverable persisted state;
- no success/synthesis occurs with eligible unfinished work;
- benchmark and quality results meet the controller contract with no unexplained regression.

If instrumentation cannot run, state the exact environmental blocker; do not mark it passed.

## Non-Goals / Guardrails

- No new architecture, prompts, categories, UI features, dependencies, or performance tuning without a failing acceptance check.
- Do not relax tests to make them pass.
- Do not approve release with unresolved data-loss, security, quality, idempotency, or unfinished-work defects.
- Do not rely on stale reports or an existing APK as evidence.

## Checkpoint / Status Instructions

Record every command and result in the controller or a linked concise validation report. If any mandatory check fails, keep this step `IN_PROGRESS` or mark `BLOCKED`; fix only the demonstrated issue, rerun affected and regression checks, and update evidence.

## Commit Checkpoint

After all mandatory checks pass, create one final focused commit, suggested message:

```text
Validate and document the optimized analysis pipeline
```

Record its short hash. Mark this step `COMPLETE`, set `Active checkpoint` to `NONE — PACK COMPLETE`, and confirm every ledger row has commit/evidence.

## STOP

Stop after the final commit and controller update. Do not begin unrelated cleanup, refactoring, or feature work. Report the final commit, commands, results, measured deltas, and any explicitly unresolved environmental validation gap.
