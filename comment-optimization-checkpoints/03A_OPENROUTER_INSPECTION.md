# Checkpoint 03A — OpenRouter Inspection and Decision Handoff

**Recommended model:** Gemini 3.5 Flash-Lite

## Entry Gate

Proceed only if 01 and 02A–02C are `COMPLETE` and the controller marks this file `IN_PROGRESS`. Read no later checkpoint.

## Scope

Inspect the **current post-Checkpoint-02 branch** and decide which proposed OpenRouter optimizations are supported by code/baseline evidence. Produce a bounded handoff. **No production, test, Gradle, Room, or configuration code changes.**

## Minimal Permitted Exploration

Read only:

- Checkpoint 01 baseline artifact and 02C validation evidence;
- `AppContainer.kt`;
- `domain/model/{GateModels,AnalysisModels}.kt`;
- `domain/batching/BatchPlanner.kt`;
- `network/adapter/{OpenRouterJevSemanticGate,OpenRouterGlmDeepAnalyzer,OpenRouterGlmSynthesisAdapter}.kt`;
- `network/dto/glm/GlmApiDtos.kt`;
- `network/{jev/JevApiService,glm/GlmApiService}.kt`;
- `network/{OpenRouterHeaderInterceptor,SafeNetworkLogging}.kt`;
- `data/local/{AnalysisOrchestrator,AnalysisPersistenceRepository}.kt`;
- `data/local/entity/{Enums,ProcessingBatchEntity,ProcessingBatchItemEntity}.kt`;
- `domain/retry/RetryPolicy.kt`;
- directly related tests only.

Do not scan unrelated UI/import/synthesis code.

## Required Evidence Handoff

Create `docs/optimization/03A_OPENROUTER_HANDOFF.md`, maximum 900 words. For each proposal below, assign `JUSTIFIED`, `SKIP`, or `DEFER`, cite current code plus Checkpoint 01 data, state the smallest file set, and state the required acceptance metric:

1. JEV as classification authority; GLM summary-only.
2. Canonical sentiment contract.
3. Serialized-payload/input/output budgeting and adaptive `max_tokens`.
4. Adaptive GLM batch sizing.
5. Shared OpenRouter client/timeouts/response bounds.
6. Item-granular JEV partial success.
7. Bounded GLM concurrency versus existing sequential loops.
8. 413/`finish_reason=length` deterministic splitting.
9. Cancellation propagation and safe response handling.
10. `Retry-After` capture/metadata handoff; defer scheduler ownership to Checkpoint 04.
11. SSE/streaming: justify with measured need or mark `SKIP` (strict structured JSON must remain atomic).

Do not assume the pre-02 code still applies. Skip optimizations already completed, unsupported by evidence, likely to regress quality, or not cost-effective.

End the handoff with:

- exact 03B scope and model: `Gemini 3.5 Flash-Lite` for local/mechanical edits; `Gemini 3.6 Flash` only if cross-layer architectural reasoning is proven;
- exact 03C scope and the same evidence-based model rule;
- explicit skipped/deferred work.

## Acceptance / Evidence Requirements

- `git diff` contains only handoff/controller documentation.
- Every decision cites current code and baseline evidence; unknowns are marked unknown.
- No proposal is automatically approved because it appeared in the old plan.
- Existing JEV/GLM quality, strict validation, persistence, retry/cancellation/idempotency, consent/security, and features are listed as invariants.

## Non-Goals

No code/tests/migrations/prompt/schema/concurrency/retry changes. No provider/web research. No reading 03B/03C/03D/04+.

## Controller / Commit

Record handoff path, decision summary, and model choices. Commit docs only:

```text
Document evidence-based OpenRouter optimization scope
```

Mark 03A `COMPLETE`, set 03B `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `03B_REQUEST_AND_BATCHING.md` or any later checkpoint. Stop after the docs-only commit/controller update.
