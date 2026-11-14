# Checkpoint 03A: OpenRouter Optimization Inspection & Decision Handoff

## Executive Summary

Based on inspection of the post-Checkpoint-02 codebase and Baseline 01 telemetry, we evaluate proposed OpenRouter optimizations to determine strict evidence-based scope for Checkpoint 03B and 03C.

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
   - **Decision:** `DEFER`
   - **Evidence:** Current baseline does not demonstrate truncation or output-budget pressure; static batch limits and default `maxTokens` are sufficient.
   - **File Set:** `BatchPlanner.kt`, `GlmApiDtos.kt`
   - **Acceptance Metric:** N/A

4. **Adaptive GLM batch sizing**
   - **Decision:** `SKIP`
   - **Evidence:** Baseline 100 passed successfully using static batch limits.
   - **File Set:** `BatchPlanner.kt`
   - **Acceptance Metric:** N/A

5. **Shared OpenRouter client/timeouts/response bounds**
   - **Decision:** `JUSTIFIED` (Client connection sharing only)
   - **Evidence:** `JevApiService` and `GlmApiService` instantiate separate `OkHttpClient` instances. We share client construction to pool connections, while preserving existing default timeout behavior without prescribing new artificial timeouts.
   - **File Set:** `JevApiService.kt`, `GlmApiService.kt`, `AppContainer.kt`
   - **Acceptance Metric:** Shared OkHttpClient pool instance across OpenRouter services with preserved default timeout behavior.

6. **Item-granular JEV partial success**
   - **Decision:** `SKIP`
   - **Evidence:** Strict batch atomic validation is required by the domain model.
   - **File Set:** `OpenRouterJevSemanticGate.kt`
   - **Acceptance Metric:** N/A

7. **Bounded GLM concurrency versus existing sequential loops**
   - **Decision:** `SKIP`
   - **Evidence:** Baseline 10/100 proved sequential GLM execution is stable.
   - **File Set:** `OpenRouterGlmDeepAnalyzer.kt`
   - **Acceptance Metric:** N/A

8. **413/`finish_reason=length` deterministic splitting**
   - **Decision:** `DEFER`
   - **Evidence:** Implementing batch splitting crosses adapter, orchestrator, persistence, and idempotency boundaries, and current baseline does not demonstrate 413 or length truncation failures.
   - **File Set:** `OpenRouterGlmDeepAnalyzer.kt`, `AnalysisOrchestrator.kt`, `ProcessingBatchEntity.kt`
   - **Acceptance Metric:** N/A

9. **Cancellation propagation and safe response handling**
   - **Decision:** `SKIP`
   - **Evidence:** Current code already propagates `CancellationException` in JEV and uses standard exception boundaries. No unhandled gap is proven in baseline testing.
   - **File Set:** N/A
   - **Acceptance Metric:** N/A

10. **`Retry-After` capture/metadata handoff; defer scheduler ownership to Checkpoint 04**
    - **Decision:** `JUSTIFIED` (Metadata/header capture only)
    - **Evidence:** `RetryPolicy` supports `serverRetryAfterSeconds`. Capturing the `Retry-After` header from 429 responses is justified, while scheduler ownership remains deferred to Checkpoint 04.
    - **File Set:** `RetryPolicy.kt`, `OpenRouterJevSemanticGate.kt`, `OpenRouterGlmDeepAnalyzer.kt`
    - **Acceptance Metric:** 429 response `Retry-After` header parsed and passed to retry policy.

11. **SSE/streaming**
    - **Decision:** `SKIP`
    - **Evidence:** Strict JSON schema validation requires atomic responses.
    - **File Set:** N/A
    - **Acceptance Metric:** N/A

## Roadmap Scope & Model Assignments

- **Exact 03B Scope:** Shared `OkHttpClient` client construction (pooling without timeout changes) and `Retry-After` response header metadata capture.
  - **Model:** `Gemini 3.5 Flash-Lite` (local/mechanical implementation).
- **Exact 03C Scope:** `SKIP` (No proven concurrency or cancellation gaps in current baseline evidence; cancellation and response handling are already robust).
  - **Model:** N/A
- **Explicit Skipped / Deferred Work:** Adaptive token budgeting/`max_tokens` (deferred), adaptive batch sizing (skipped), item-granular JEV partial success (skipped), GLM concurrency increases (skipped), 413/truncation batch splitting (deferred), SSE/streaming (skipped), cancellation/safe-response additions (skipped due to no proven gap), and WorkManager retry scheduler ownership (deferred to Checkpoint 04).
