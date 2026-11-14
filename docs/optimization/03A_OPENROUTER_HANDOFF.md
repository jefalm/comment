# Checkpoint 03A: OpenRouter Optimization Inspection & Decision Handoff

## Executive Summary

Based on inspection of the post-Checkpoint-02 codebase (`AppContainer`, `OpenRouterJevSemanticGate`, `OpenRouterGlmDeepAnalyzer`, `OpenRouterGlmSynthesisAdapter`, `BatchPlanner`, `RetryPolicy`, and DTOs) and Baseline 01 telemetry, we evaluate 11 proposed OpenRouter optimizations to determine justification for Checkpoint 03B and 03C.

## Proposal Decisions

1. **JEV as classification authority; GLM summary-only**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** Established in Ch. 02; `OpenRouterJevSemanticGate` handles quality/category/sentiment; GLMs handle deep analysis and synthesis without overriding JEV counts.
   - **File Set:** `OpenRouterJevSemanticGate.kt`, `OpenRouterGlmDeepAnalyzer.kt`, `OpenRouterGlmSynthesisAdapter.kt`
   - **Acceptance Metric:** Zero classification authority regression across test suites.

2. **Canonical sentiment contract**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** `ALLOWED_CATEGORIES` and `ALLOWED_SENTIMENTS` are aligned across JEV and GLM adapters.
   - **File Set:** `OpenRouterJevSemanticGate.kt`, `OpenRouterGlmDeepAnalyzer.kt`
   - **Acceptance Metric:** Sentiment enum consistency in validation checks.

3. **Serialized-payload/input/output budgeting and adaptive `max_tokens`**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** `BatchPlanner` provides byte/token estimates; GLM requests use fixed `maxTokens = 4096`. Dynamic token budgeting prevents truncation on large comments.
   - **File Set:** `BatchPlanner.kt`, `GlmApiDtos.kt`, `OpenRouterGlmDeepAnalyzer.kt`
   - **Acceptance Metric:** Token estimation within 10% of payload size; zero unexpected truncations.

4. **Adaptive GLM batch sizing**
   - **Decision:** `SKIP`
   - **Evidence:** Baseline 100 passed successfully in 5 batches using static limits (`BatchPlanner.GLM_LIMITS`). Current static constraints are sufficient.
   - **File Set:** `BatchPlanner.kt`
   - **Acceptance Metric:** N/A

5. **Shared OpenRouter client/timeouts/response bounds**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** `JevApiService` and `GlmApiService` instantiate separate `OkHttpClient` instances without explicit timeouts or shared connection pools.
   - **File Set:** `JevApiService.kt`, `GlmApiService.kt`, `AppContainer.kt`
   - **Acceptance Metric:** Shared OkHttpClient pool with 30s read/write timeouts.

6. **Item-granular JEV partial success**
   - **Decision:** `SKIP`
   - **Evidence:** Strict batch atomic validation is required by the domain model and database entities. Partial success adds unnecessary complexity.
   - **File Set:** `OpenRouterJevSemanticGate.kt`
   - **Acceptance Metric:** N/A

7. **Bounded GLM concurrency versus existing sequential loops**
   - **Decision:** `SKIP`
   - **Evidence:** Baseline 10/100 proved sequential GLM execution is stable, predictable, and avoids rate limit (429) spikes.
   - **File Set:** `OpenRouterGlmDeepAnalyzer.kt`
   - **Acceptance Metric:** N/A

8. **413/`finish_reason=length` deterministic splitting**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** `BatchStatus.SPLIT` exists in database entities, but current GLM adapter returns error on `finish_reason == "length"` rather than splitting.
   - **File Set:** `OpenRouterGlmDeepAnalyzer.kt`, `AnalysisOrchestrator.kt`, `ProcessingBatchEntity.kt`
   - **Acceptance Metric:** Truncated or oversized batches automatically split into child batches.

9. **Cancellation propagation and safe response handling**
   - **Decision:** `JUSTIFIED`
   - **Evidence:** JEV adapter handles `CancellationException`, but GLM adapters should uniformly propagate coroutine cancellation to avoid orphaned network requests.
   - **File Set:** `OpenRouterJevSemanticGate.kt`, `OpenRouterGlmDeepAnalyzer.kt`, `OpenRouterGlmSynthesisAdapter.kt`
   - **Acceptance Metric:** Immediate job cancellation halts OkHttp calls without coroutine leaks.

10. **`Retry-After` capture/metadata handoff; defer scheduler ownership to Checkpoint 04**
    - **Decision:** `JUSTIFIED`
    - **Evidence:** `RetryPolicy` supports `serverRetryAfterSeconds`, but network adapters do not yet parse the HTTP `Retry-After` header.
    - **File Set:** `RetryPolicy.kt`, `OpenRouterJevSemanticGate.kt`, `OpenRouterGlmDeepAnalyzer.kt`
    - **Acceptance Metric:** 429 response `Retry-After` header populated into retry delay.

11. **SSE/streaming**
    - **Decision:** `SKIP`
    - **Evidence:** Strict JSON schema validation (`response_format` with JSON schema) requires atomic responses. Streaming conflicts with guaranteed schema enforcement.
    - **File Set:** N/A
    - **Acceptance Metric:** N/A

## Roadmap Scope & Model Assignments

- **Exact 03B Scope:** Request building, token budgeting, shared OkHttpClient/timeouts, and `Retry-After` header capture.
  - **Model:** `Gemini 3.5 Flash-Lite` (local/mechanical implementation).
- **Exact 03C Scope:** Concurrency guardrails, cancellation propagation, and 413/truncation batch splitting logic.
  - **Model:** `Gemini 3.5 Flash-Lite` (local/mechanical implementation; `Gemini 3.6 Flash` reserved if cross-layer concurrency synchronization proves complex).
- **Explicit Skipped / Deferred Work:** Adaptive batch sizing, item-granular JEV partial success, bounded GLM concurrency increases, SSE/streaming, and WorkManager retry scheduler ownership (deferred to Checkpoint 04).
