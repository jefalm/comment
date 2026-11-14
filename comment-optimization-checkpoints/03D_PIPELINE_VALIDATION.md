# Checkpoint 03D — Pipeline Validation and Readiness

**Recommended model:** Gemini 3.5 Flash-Lite

## Entry Gate

Proceed only if 03A–03C are `COMPLETE` and this file is `IN_PROGRESS`. Read no later checkpoint.

## Scope

Add/update focused tests, run required checks, compare against Checkpoint 01, verify every justified 03A decision, make only narrow defect fixes, and close Checkpoint 03. No implementation redesign.

## Minimal Permitted Exploration

Read only:

- 03A handoff; 03B/03C controller evidence and diffs;
- Checkpoint 01 baseline artifact;
- tests directly covering changed request/planner/network/adapter/orchestrator/persistence code;
- changed production files only to diagnose a failing check.

Do not inspect UI/import/scheduling areas or begin Checkpoint 04.

## Required Tests and Evidence

Focused tests must cover every implemented item, including as applicable:

- JEV classification authority and GLM summary-only strict schema/exact IDs;
- sentiment consistency and historical-schema compatibility;
- serialized budget, adaptive `max_tokens`, deterministic batch boundaries, and oversized units;
- JEV partial success/retry-only-failures and out-of-order association;
- JEV/GLM/host concurrency caps and sibling isolation;
- shared-client timeouts/headers/redaction/response bounds;
- cancellation reaching the socket and recoverable persisted state;
- 413/length split lineage, unit failure, and idempotency;
- `Retry-After` capture without implementing Checkpoint 04 scheduling;
- skipped/deferred 03A proposals remain unimplemented.

Run and record:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Compare final behavior with Checkpoint 01 using the same fixtures. Report request counts, p50/p95 call latency, first committed result, total duration, tokens, cost, retries/splits, completed comments/minute, and quality/error outcomes. Label synthetic timings; no unsupported performance claim.

## Acceptance Requirements

- Every `JUSTIFIED` 03A item is implemented/tested or explicitly blocked; every `SKIP`/`DEFER` item remains untouched.
- Only JEV-passed comments reach GLM/synthesis.
- Strict schema/exact IDs, JEV/GLM quality, summaries, persistence, retry/cancellation/idempotency, consent/security, history, and existing features show no regression.
- No secret/full sensitive body appears in logs/telemetry.
- Baseline comparison shows no unexplained quality/error regression; performance/cost changes are reported honestly.
- Checkpoint 04 work was not implemented early.

Only narrow fixes directly required by failed checks are allowed. If the fix changes architecture, schema/migration strategy, or handoff decisions, mark `BLOCKED` and return to 03B/03C in a later session.

## Non-Goals

No new optimization, concurrency tuning, SSE adoption, scheduler/UI/synthesis redesign, unrelated refactor, or test weakening.

## Controller / Commit

Record commands/results, comparison artifact, justified/skipped/deferred disposition, limits, versions, and any environmental gap. Commit:

```text
Validate the optimized OpenRouter pipeline
```

Mark 03D `COMPLETE`, set `04_PROCESSING_PERSISTENCE_UI.md` `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `04_PROCESSING_PERSISTENCE_UI.md`, `05_FINAL_VALIDATION.md`, or unrelated work. Stop after the validation commit/controller update.
