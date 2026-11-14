# Checkpoint 02C — JEV Quality Validation and Readiness

**Recommended model:** Gemini 3.5 Flash-Lite

## Entry Gate

Proceed only if 02A and 02B are `COMPLETE` and the controller marks this file `IN_PROGRESS`. Read no later checkpoint.

## Scope

Add/update focused tests and fixtures, run validation, verify acceptance, make only narrow defect fixes, and close Checkpoint 02. Do not redesign the 02B implementation.

## Relevant Test Areas

- filter, fingerprint/deduplication, semantic-gate contract, orchestrator, persistence, config bootstrap, and Room migration tests under `app/src/test/`
- instrumentation migration tests under `app/src/androidTest/` when required
- changed 02B files only for narrow corrections revealed by tests

## Required Validation

Create a fixed anonymized labeled corpus covering:

- valuable one-/two-character or one-/two-word replies and short questions;
- praise, feedback, criticism, disagreement, negative sentiment, unpopular/minority opinions;
- exact source/fingerprint duplicates and same-text-but-distinct comments;
- spam/promotion;
- toxic-only abuse versus harsh substantive criticism;
- incoherent, irrelevant, meaningless boilerplate, obvious trash, link-heavy, and ambiguous cases.

Tests must prove:

- deterministic rules do not infer toxicity, relevance, sentiment, or viewpoint from keywords;
- valuable short/ambiguous/substantive dissent reaches JEV or review and can pass;
- exact duplicates avoid repeated JEV/GLM work only within the approved scope while provenance/counting remains auditable;
- local pass always routes to JEV; local/JEV reject and manual review never route to GLM;
- reject/manual-review/pass boundaries honor configurable thresholds;
- rule/config versions persist; supported historical databases/runs keep their original meaning;
- consent, strict validation, credentials, cancellation, leases, and idempotency remain unchanged.

Run and record:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Run relevant migration/instrumentation tests when the 02B change requires them. If unavailable, record the exact environmental blocker; never mark them passed.

## Acceptance Checks

- Every original Checkpoint 02 requirement is covered by code evidence or a passing test.
- No quality regression protects trash at the expense of meaningful short/dissenting/minority content.
- No new broad blocklist or text-only cross-comment deduplication exists.
- No scope from Checkpoint 03+ was implemented.
- Controller evidence lists corpus location, rule/config/migration versions, commands/results, and any unchanged thresholds.

Only narrow fixes directly required by failed checks are allowed. If a fix changes the design or migration, mark `BLOCKED` and return to 02B in a later session; do not improvise.

## Non-Goals / Guardrails

- No OpenRouter concurrency/batching/retry/GLM redesign.
- No new product behavior beyond validating 02B.
- Do not weaken or delete tests to pass.
- Do not read or start Checkpoint 03.

## Checkpoint / Status Instructions

Keep 02C `IN_PROGRESS` until mandatory checks pass. Record exact failures/blockers. When ready, confirm all 02A/02B/02C rows have commits and evidence; this is the only condition that completes aggregate Checkpoint 02.

## Commit Checkpoint

Commit tests, narrow fixes, and validation documentation, suggested message:

```text
Validate JEV input-quality checkpoint
```

Record the hash, mark 02C `COMPLETE`, set `03_OPENROUTER_PIPELINE.md` to `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `03_OPENROUTER_PIPELINE.md` or any later checkpoint in this session. Stop immediately after the validation commit and controller update.
