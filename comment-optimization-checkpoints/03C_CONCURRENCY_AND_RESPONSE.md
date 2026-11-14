# Checkpoint 03C — Concurrency and Response Handling

## Model Selection

Use the 03A handoff decision:

- **Gemini 3.5 Flash-Lite** for proven local/mechanical changes.
- **Gemini 3.6 Flash** only when 03A proves cross-layer concurrency/state reasoning is necessary.

Record the choice/evidence.

## Entry Gate

Proceed only if 03A–03B are `COMPLETE` and this file is `IN_PROGRESS`. Read only the controller, 03A handoff, 03B evidence, and permitted files. Do not read 03D/04+.

## Scope

Implement only 03A items marked `JUSTIFIED` for costly sequential work, bounded concurrency, item-level response outcomes, cancellation, shared HTTP behavior, response bounds, retry metadata interaction, and 413/truncation splitting.

## Minimal Permitted Exploration

Only handoff-approved files, expected among:

- `AppContainer.kt`;
- `network/adapter/{OpenRouterJevSemanticGate,OpenRouterGlmDeepAnalyzer,OpenRouterGlmSynthesisAdapter}.kt`;
- `network/{jev/JevApiService,glm/GlmApiService}.kt`;
- `network/{OpenRouterHeaderInterceptor,SafeNetworkLogging}.kt`;
- `data/local/{AnalysisOrchestrator,AnalysisPersistenceRepository}.kt`;
- `data/local/entity/{Enums,ProcessingBatchEntity,ProcessingBatchItemEntity}.kt`;
- `domain/retry/RetryPolicy.kt` only for response classification/metadata compatibility;
- focused concurrency/network/persistence tests.

Do not inspect UI/import code or redesign Checkpoint 04 scheduling.

## Required Outcomes

Implement only justified items:

- Preserve exact JEV `commentId`/order; persist per-item success/failure so one 429/5xx/malformed item cannot erase sibling successes; retry only failures.
- Keep JEV concurrency bounded (current default 4 unless baseline supports another value).
- Replace proven sequential GLM bottlenecks with bounded independent execution, starting at 2 only if justified; atomically claim leases, run network outside transactions, persist each completion independently, and do not cancel successful siblings.
- Share/configure OpenRouter `OkHttpClient` only if justified: explicit connect/read/write/call timeouts, pool/dispatcher/per-host limits, cancellation, attribution/authentication, header redaction, safe timing, and response-size bounds.
- On 413 or `finish_reason=length`, deterministically split a multi-item batch, mark the parent `SPLIT`, and fail only an oversized unit item. Never retry unchanged oversized/truncated work indefinitely.
- Capture safe `Retry-After`/status metadata for existing retry decisions. **Do not implement earliest-due WorkManager scheduling; that belongs to Checkpoint 04.**
- Propagate cancellation to OkHttp and leave recoverable persisted state without duplicates.
- Keep structured JSON atomic. Implement SSE only if 03A marked it `JUSTIFIED`; otherwise skip it.

## Acceptance / Evidence Requirements

Focused tests prove concurrency maxima, out-of-order ID association, partial JEV success, sibling isolation, lease exclusivity, cancellation-to-socket behavior, response bounds, split lineage/idempotency, and safe retry metadata. Compare measured sequential/parallel timings to Checkpoint 01 without claiming production speed from synthetic delay alone.

## Non-Goals / Guardrails

- No request/schema/batch-budget redesign from 03B.
- No retry scheduler, large-run coordinator, synthesis query, or UI work from Checkpoint 04.
- No concurrency increase unsupported by 03A/baseline.
- No weakening schemas/IDs, quality gates, consent/security, persistence, retry semantics, or history.
- No production body/secret logging.

## Controller / Commit

Record model choice, justified items implemented, concurrency limits, timing evidence, response/split policy, files, and focused tests. Commit:

```text
Add bounded OpenRouter execution and response recovery
```

Mark 03C `COMPLETE`, set 03D `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `03D_PIPELINE_VALIDATION.md` or any later checkpoint. Stop after the commit/controller update.
