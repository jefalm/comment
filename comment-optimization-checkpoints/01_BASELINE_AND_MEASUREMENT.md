# Checkpoint 01 — Baseline and Measurement

## Entry Gate

Proceed only if `00_README_CONTROL.md` marks this file `IN_PROGRESS`. Read no later checkpoint file.

## Scope

Add safe, deterministic measurement without changing pipeline behavior. Establish request count, latency, token/cost, retry, persistence, and throughput baselines for the current local-filter -> JEV -> GLM -> synthesis flow.

## Relevant Code Areas

Paths are relative to `app/src/main/java/com/example/commentanalyzer/` unless stated otherwise.

- `data/local/AnalysisOrchestrator.kt`
- `data/local/AnalysisPersistenceRepository.kt`
- `network/adapter/OpenRouterJevSemanticGate.kt`
- `network/adapter/OpenRouterGlmDeepAnalyzer.kt`
- `network/adapter/OpenRouterGlmSynthesisAdapter.kt`
- `worker/AnalysisWorker.kt`
- tests under `app/src/test/java/com/example/commentanalyzer/`

## Required Outcomes

- Add injectable run/stage/batch telemetry or an equivalent test-visible recorder.
- Record counts: discovered, locally rejected/reviewed/passed, JEV rejected/reviewed/passed, GLM queued/completed, retrying, and failed.
- Record actual JEV, GLM-analysis, and synthesis request counts.
- Record safe timings: queue, network, validation, Room commit, time to first committed result, and total run.
- Record request item count/serialized bytes, estimated and actual tokens, cost, model, HTTP status, retries, splits, cancellations, and lease recovery.
- Add `MockWebServer` baseline scenarios for 10 and 100 comments with configurable delay plus 429, 413, 5xx, timeout, truncation, malformed JSON, and cancellation.
- Save baseline results in a concise repository document or test artifact. Clearly label synthetic timings.

Production telemetry must not contain keys, Authorization values, full comments, prompts, or full responses.

## Acceptance Checks

- Tests can assert exact request counts by endpoint and exact item/result association.
- Baseline captures p50/p95 call latency, first-result time, total duration, tokens/cost, retries, and completed comments/minute.
- Existing behavior and tests remain unchanged except for instrumentation.
- Run at least:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Record exact outcomes; do not claim unrun checks.

## Non-Goals / Guardrails

- Do not change prompts, thresholds, batching, concurrency, retry behavior, database schema, or UI behavior.
- Do not “optimize” while measuring.
- Do not add production body logging.
- Preserve consent, credential redaction, cancellation, leases, strict validation, and history.

## Checkpoint / Status Instructions

1. Before edits, record the starting commit in `00_README_CONTROL.md`.
2. Keep this row `IN_PROGRESS` while working.
3. After checks pass, record commands and baseline artifact location in the ledger.
4. If blocked, mark `BLOCKED` with exact evidence and stop.

## Commit Checkpoint

Create one focused commit, suggested message:

```text
Add analysis pipeline baseline telemetry
```

Record its short hash in the controller. Mark this step `COMPLETE`, set `02_JEV_INPUT_QUALITY.md` to `IN_PROGRESS`, update `Active checkpoint`, then stop.

## STOP

Do not read or implement `02_JEV_INPUT_QUALITY.md` or any later file in this session. Stop after the controller and commit are updated.
