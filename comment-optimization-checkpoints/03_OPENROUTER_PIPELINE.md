# Checkpoint 03 — OpenRouter Pipeline

## Entry Gate

Proceed only if Checkpoints 01–02 are `COMPLETE` and the controller marks this file `IN_PROGRESS`. Read no later checkpoint file.

## Scope

Reduce wasted OpenRouter work while preserving JEV quality. Make JEV outcomes item-granular, make JEV authoritative for classification, change GLM to summary-only for JEV-passed comments, add adaptive GLM batching/concurrency/splitting, and share a bounded HTTP client.

## Relevant Code Areas

- `AppContainer.kt`
- `domain/model/GateModels.kt`
- `domain/model/AnalysisModels.kt`
- `domain/batching/BatchPlanner.kt`
- `network/adapter/OpenRouterJevSemanticGate.kt`
- `network/adapter/OpenRouterGlmDeepAnalyzer.kt`
- `network/adapter/OpenRouterGlmSynthesisAdapter.kt`
- `network/dto/glm/GlmApiDtos.kt`
- `network/jev/JevApiService.kt`
- `network/glm/GlmApiService.kt`
- `network/OpenRouterHeaderInterceptor.kt`
- `network/SafeNetworkLogging.kt`
- `data/local/AnalysisOrchestrator.kt`
- `data/local/AnalysisPersistenceRepository.kt`

## Required Outcomes

### Item-granular JEV

- Preserve exact `commentId` association and input order.
- Return/persist success or failure per item.
- Do not discard successful JEV decisions because a sibling gets 429/5xx/malformed output.
- Retry only failed items; represent partial batches with existing `PARTIAL`/child-batch mechanisms or an equivalent persisted design.
- Keep global JEV concurrency bounded; default to 4 until the baseline justifies a change.

### Single classification authority

For new schema/config versions:

- JEV owns category, sentiment, category/sentiment confidence, and quality probability.
- Persist JEV quality probability as the passed comment’s quality/relevance score.
- GLM returns only exact `commentId` plus concise `summary` under strict JSON schema.
- GLM persistence retains JEV classification; it must not overwrite it.
- Keep historical schemas/results readable.
- Canonicalize sentiment end to end. Prefer JEV’s current positive/neutral/negative set; if `mixed` is retained, support it consistently everywhere.

### Adaptive GLM execution

Budget each request using serialized messages, IDs/envelope, schema overhead, text, expected summary output, context limit, and safety margin. Set `max_tokens` per batch instead of fixed 4096.

Run independent GLM batches with bounded concurrency, starting at 2. Claim leases atomically, perform network outside Room transactions, persist each completion independently, and propagate cancellation.

On 413 or `finish_reason=length`:

- split multi-item batches into deterministic children;
- mark the parent `SPLIT`;
- permanently fail only an oversized unit item;
- never retry an unchanged oversized/truncated batch indefinitely.

### Shared HTTP stack

Inject one shared OpenRouter `OkHttpClient` with explicit connect/read/write/call timeouts, pool/dispatcher/per-host limits, cancellation, attribution/authentication, safe timing, header redaction, and response-size bounds.

Streaming is optional. Never expose/persist partial structured JSON before complete schema and ID validation.

## Acceptance Checks

- Mixed JEV success/error persists successes and retries only failures.
- New GLM request/response contains only IDs and summaries; JEV classifications remain unchanged.
- Only JEV-passed comments reach GLM/synthesis.
- Actual concurrency never exceeds configured JEV/GLM/host limits; out-of-order completion preserves IDs.
- Adaptive budgets and `max_tokens` are asserted from recorded request bodies.
- 413/truncation creates deterministic children; a unit failure affects one item.
- Canceling the coroutine cancels OkHttp calls.
- Logs/telemetry contain no key, bearer value, or full sensitive body.
- Baseline comparison reports request count, first-result time, total duration, p50/p95, tokens, cost, retries, and throughput.
- Run focused network/concurrency tests plus the standard unit/lint/debug build checks.

## Non-Goals / Guardrails

- Do not remove JEV or convert it to one batch call; the inspected Decisions API adapter sends one `state` per request.
- Do not route rejected/manual-review comments to GLM.
- Do not weaken strict schema, exact IDs, consent, credential security, or cancellation.
- Do not raise concurrency without baseline evidence.
- Do not implement retry scheduling/UI changes assigned to Checkpoint 04.

## Checkpoint / Status Instructions

Record schema/config versions, effective concurrency, benchmark delta, and test commands in the controller. If provider behavior prevents the required strict contract, mark `BLOCKED` with captured safe evidence and stop.

## Commit Checkpoint

Create one focused commit, suggested message:

```text
Optimize the validated OpenRouter analysis pipeline
```

Record its short hash. Mark this step `COMPLETE`, set `04_PROCESSING_PERSISTENCE_UI.md` to `IN_PROGRESS`, update `Active checkpoint`, then stop.

## STOP

Do not read or implement `04_PROCESSING_PERSISTENCE_UI.md` or `05_FINAL_VALIDATION.md` in this session. Stop after the controller update.
