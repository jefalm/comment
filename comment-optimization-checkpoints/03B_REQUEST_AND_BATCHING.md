# Checkpoint 03B — Request Construction and Batching

## Model Selection

Use the model mandated by the 03A handoff:

- **Gemini 3.5 Flash-Lite** when changes are mechanical/local.
- **Gemini 3.6 Flash** only when 03A proves cross-layer architectural reasoning is necessary.

Record the choice/evidence in the controller.

## Entry Gate

Proceed only if 03A is `COMPLETE`, its handoff exists, and this file is `IN_PROGRESS`. Read the controller, this file, 03A handoff, and only the permitted files below. Do not read 03C/03D/later checkpoints.

## Scope

Implement only 03A items marked `JUSTIFIED` that affect request contracts, payload/token efficiency, and GLM batch planning. Do not implement concurrency, partial response recovery, cancellation, retries, streaming, or scheduling.

## Minimal Permitted Exploration

Only handoff-approved files, expected among:

- `domain/model/AnalysisModels.kt`;
- `domain/batching/BatchPlanner.kt` or one focused GLM planner;
- `network/dto/glm/GlmApiDtos.kt`;
- `network/adapter/OpenRouterGlmDeepAnalyzer.kt`;
- `data/local/{AnalysisOrchestrator,AnalysisPersistenceRepository}.kt` only if required for the request contract;
- corresponding focused unit tests.

Do not inspect broad UI/import/worker code.

## Required Outcomes

Implement only if 03A marks them `JUSTIFIED`:

- Make JEV authoritative for category, sentiment, confidences, and quality probability for new versioned runs.
- Make per-comment GLM output strict `commentId + concise summary`; retain JEV values and historical schemas/results.
- Resolve sentiment mismatch consistently without rewriting history.
- Budget from serialized messages, IDs/envelope, strict schema, text, expected output, context limit, and safety margin.
- Set `max_tokens` per planned batch rather than fixed 4096.
- Size GLM batches deterministically by item/bytes/input/output/context limits; persist planned size/budget metadata where the current schema already supports it or the handoff explicitly approves a migration.
- Keep exact ID-set/schema validation and JEV-pass-only GLM eligibility.

Mechanical request slimming must not remove required safety instructions, response schema strictness, usage/cost/model metadata, or per-comment summaries.

## Acceptance / Evidence Requirements

- Recorded request bodies prove the intended schema, fields, `max_tokens`, and deterministic batch boundaries.
- JEV classifications remain unchanged after GLM completion.
- Repeated identical planning input produces identical batches.
- Oversized unit inputs are detected without silent truncation; response-driven splitting belongs to 03C.
- Historical config/schema behavior remains readable.
- Focused request/planner/persistence tests pass. Full suite belongs to 03D.

## Non-Goals / Guardrails

- No JEV concurrency/partial-success changes.
- No parallel GLM execution, shared dispatcher tuning, response streaming, 413/length split execution, retry scheduling, or UI.
- No prompt/field removal unsupported by 03A.
- No weakened strict validation, consent, security, cancellation, persistence, or idempotency.

## Controller / Commit

Record model choice, handoff decision IDs implemented/skipped, schema/config versions, files, and focused checks. Commit:

```text
Optimize OpenRouter requests and GLM batch planning
```

Mark 03B `COMPLETE`, set 03C `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `03C_CONCURRENCY_AND_RESPONSE.md` or any later checkpoint. Stop after the commit/controller update.
