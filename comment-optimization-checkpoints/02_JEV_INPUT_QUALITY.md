# Checkpoint 02 — JEV Input Quality

## Entry Gate

Proceed only if Checkpoint 01 is `COMPLETE` and `00_README_CONTROL.md` marks this file `IN_PROGRESS`. Read no later checkpoint file.

## Scope

Improve the repository’s pre-GLM quality layer: conservative deterministic local filtering followed by mandatory JEV semantic gating. Filter trash, spam, toxic-only, incoherent, irrelevant, exact-duplicate, and low-information comments while retaining meaningful short comments, questions, criticism, disagreement, negative sentiment, and minority opinions.

## Relevant Code Areas

- `domain/filter/LocalFilter.kt`
- `domain/model/GateModels.kt`
- `domain/model/AnalysisModels.kt`
- `domain/fingerprint/Fingerprint.kt`
- `network/adapter/OpenRouterJevSemanticGate.kt`
- `data/local/AnalysisOrchestrator.kt`
- `data/local/AnalysisPersistenceRepository.kt`
- `data/local/entity/ProcessingConfigEntity.kt`
- `data/local/ProcessingConfigBootstrap.kt`
- related filter/gate/orchestrator/persistence tests

## Required Outcomes

### Local deterministic filter

- Keep rules deterministic, explainable, versioned, and high precision.
- Reject empty/unusable payloads, obvious repeated-character trash, and exact duplicates using existing stable identity/fingerprint data or a documented equivalent.
- Treat ambiguous link-heavy input as review/JEV, not automatic rejection.
- Add low-information rejection only when structure is unambiguously non-contributing; do not use length alone beyond truly unusable input.
- Persist rule IDs and filter version.

### JEV semantic gate

For a new configuration version, make the contract explicit:

- **Pass:** usable opinion, question, critique, praise, factual observation, or actionable feedback relevant to the analyzed content.
- **Reject:** spam/promotion, toxicity or abuse without useful substance, incoherence, irrelevance, meaningless boilerplate, or otherwise non-contributing content.
- **Manual review:** uncertain probability between configurable reject/pass thresholds.

Keep defaults `reject <= 0.3`, `pass >= 0.7` unless measured labeled-corpus evidence supports a versioned change. Negative, critical, dissenting, unpopular, or minority-view comments are not low quality by definition.

### Quality corpus

Add a fixed, anonymized labeled fixture covering:

- valuable one-/two-word replies and short questions;
- criticism, disagreement, negative sentiment, and minority opinions;
- praise/feedback;
- exact duplicates;
- spam/promotion;
- toxic-only abuse versus harsh but substantive criticism;
- incoherent, irrelevant, boilerplate, and ambiguous cases.

Tests must validate routing and protect against viewpoint/sentiment filtering.

## Acceptance Checks

- Exact duplicates do not consume JEV/GLM again within the defined scope; provenance/counting remains auditable.
- Valuable short comments and substantive disagreement reach JEV and can pass.
- Deterministic rules never infer toxicity, relevance, sentiment, or viewpoint from keyword lists.
- JEV remains mandatory before GLM for locally passed comments.
- Reject/manual-review/pass state transitions and threshold boundaries are tested.
- New config/filter versions do not reinterpret historical runs.
- Run focused tests plus:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

## Non-Goals / Guardrails

- Do not remove or bypass JEV; it is an OpenRouter semantic stage, not an offline classifier.
- Do not send locally/JEV-rejected or manual-review comments to GLM.
- Do not add broad blocklists for language, politics, identity, profanity, criticism, or sentiment.
- Do not change OpenRouter batching/concurrency/retry yet.
- Do not delete historical results or weaken consent/security/schema checks.

## Checkpoint / Status Instructions

Keep this row `IN_PROGRESS`; record the corpus location, rule/config versions, focused test results, and any deliberate threshold decision in the controller. On failure, mark `BLOCKED` and stop.

## Commit Checkpoint

Create one focused commit, suggested message:

```text
Strengthen local and JEV input quality gates
```

Record its short hash. Mark this step `COMPLETE`, set `03_OPENROUTER_PIPELINE.md` to `IN_PROGRESS`, update `Active checkpoint`, then stop.

## STOP

Do not read or implement `03_OPENROUTER_PIPELINE.md` or later files in this session. Stop after committing and updating the controller.
