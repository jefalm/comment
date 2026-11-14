# Checkpoint 02B — JEV Input-Quality Implementation

**Recommended model:** Gemini 3.6 Flash

## Entry Gate

Proceed only if 02A is `COMPLETE`, its handoff exists, and the controller marks this file `IN_PROGRESS`. Read the controller, this file, and the 02A handoff only; do not read 02C or later checkpoints.

## Scope

Implement only the difficult local-filter/JEV quality changes specified by the evidence-based 02A handoff. Do not redesign OpenRouter batching, item-level retries, GLM authority, processing UI, or the validation suite.

## Relevant Code Areas

Use only handoff-confirmed files, expected among:

- `domain/filter/LocalFilter.kt`
- `domain/model/GateModels.kt`
- `domain/fingerprint/Fingerprint.kt`
- `data/local/AnalysisOrchestrator.kt`
- `data/local/AnalysisPersistenceRepository.kt`
- `data/local/entity/ProcessingConfigEntity.kt`
- `data/local/ProcessingConfigBootstrap.kt`
- `data/local/AppDatabase.kt`

## Required Outcomes

### Deterministic local layer

- Keep rules explainable, versioned, persisted, and high precision.
- Reject blank/unusable payloads and unmistakable repeated-character/boilerplate trash.
- Remove the current assumption that text shorter than three characters is automatically low quality. Meaningful one-/two-character or one-/two-word comments and short questions must reach JEV unless objectively unusable.
- Treat link-heavy, profanity-containing, harsh, or otherwise ambiguous content as JEV/review input—not local rejection.
- Deduplicate only exact records under the handoff’s source/fingerprint scope. Keep the canonical record and persist an auditable duplicate rule/result for the duplicate. Do not collapse different authors/source IDs/timestamps or comments merely sharing normalized text.
- Add low-information rejection only for structurally certain non-content; ambiguity defaults to JEV/manual review.

### JEV semantic layer

Create a new versioned contract/config if required by the handoff:

- **Pass:** relevant usable opinion, question, critique, praise, factual observation, or actionable feedback.
- **Reject:** spam/promotion, toxic/abusive text without useful substance, incoherence, irrelevance, meaningless boilerplate, or otherwise non-contributing input.
- **Manual review:** uncertain probability between configurable thresholds.

Retain defaults `reject <= 0.3` and `pass >= 0.7` unless the completed baseline/handoff contains measured labeled evidence for a versioned change.

### Explicit fairness/content safeguards

- Never equate brevity with low quality.
- Never reject criticism, disagreement, negative sentiment, unpopular/minority opinions, or political/identity content because of viewpoint or tone.
- Harsh but substantive criticism must be eligible to pass; toxic-only abuse may be rejected.
- No subjective keyword/blocklist inference for toxicity, relevance, sentiment, language, identity, or ideology.
- Locally passed comments must still go through JEV; rejected/manual-review comments must not go to GLM.

### Compatibility

Persist filter rule IDs/version and configuration version. Preserve existing rows and semantics. Add only the handoff-approved migration; never use destructive migration or reinterpret historical runs.

## Acceptance Checks

Implementation review must confirm:

- all handoff decisions are implemented and no extra scope was added;
- short/ambiguous/protected viewpoints default to JEV/review rather than local rejection;
- exact duplicate handling is deterministic and auditable;
- JEV remains mandatory and threshold routing remains intact;
- no existing safety/history contract is weakened.

02C owns corpus tests, full test/lint/build, and acceptance sign-off. In 02B, do not redesign tests; use a compiler-only check only if needed to resolve syntax/type errors, and record it honestly.

## Non-Goals / Guardrails

- No item-granular JEV failure handling, concurrency, batching, retry, shared HTTP, GLM summary-only, synthesis, or UI work.
- No broad test-suite work; no threshold tuning without evidence.
- No deletion/reinterpretation of historical data.
- No secret/body logging or security relaxation.

## Checkpoint / Status Instructions

Record changed files, filter/config/migration versions, duplicate scope, and any compiler-only result in the 02B row. If implementation would require unapproved architecture or unsafe migration, mark `BLOCKED` and STOP.

## Commit Checkpoint

Create one implementation-only commit, suggested message:

```text
Implement protected local and JEV quality gating
```

Record the hash, mark 02B `COMPLETE`, set 02C `IN_PROGRESS`, update `Active checkpoint`, then STOP.

## STOP

Do not read or implement `02C_JEV_VALIDATION.md` or any later checkpoint in this session. Stop immediately after the implementation commit and controller update.
