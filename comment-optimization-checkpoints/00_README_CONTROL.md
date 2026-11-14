# Comment Optimization Checkpoint Controller

## Purpose

This file is the sole status and sequencing source of truth for the `comment` optimization. Work only in this repository. Read this file first, then read **only the active checkpoint file**. Do not inspect or implement later checkpoint files.

## Non-Negotiable Product Contract

Preserve YouTube/TXT import, Room history/migrations, WorkManager recovery, encrypted credentials, explicit remote-processing consent, strict response schemas, exact ID validation, classifications, per-comment summaries, synthesis, evidence navigation, usage/cost metadata, cancellation, leases, and idempotency.

Input-quality architecture:

```text
Import -> deterministic local filter -> JEV semantic quality gate
       -> reject / manual review / pass -> batched GLM summaries -> synthesis
```

JEV remains mandatory for the default remote pipeline. The current local filter only detects blank/very short/repeated-character/link-heavy input; JEV supplies the semantic decision for spam, toxic, incoherent, irrelevant, or otherwise non-contributing comments. Improve both layers without rejecting valuable short comments, questions, criticism, disagreement, negative sentiment, or minority opinions. Exact duplicates may be removed deterministically.

JEV uses OpenRouter and is not an offline model. “Input-quality gate” means the repository stage before GLM, combining deterministic local rules with the remote JEV semantic gate.

## Execution Protocol

1. Confirm the worktree state and record the starting commit below.
2. Set exactly one checkpoint to `IN_PROGRESS`.
3. Read only that checkpoint file and the repository files it names.
4. Implement only its scope; run its acceptance checks.
5. Commit the checkpoint. Record commit and evidence here.
6. Mark it `COMPLETE`, set the next checkpoint `IN_PROGRESS`, then stop the session.
7. A later session may continue from this controller.

Allowed status values: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

## Status Ledger

- Starting commit: `UNSET`
- Active checkpoint: `01_BASELINE_AND_MEASUREMENT.md`

| Step | File | Status | Commit | Evidence / blocker |
|---|---|---|---|---|
| 1 | `01_BASELINE_AND_MEASUREMENT.md` | `IN_PROGRESS` | `UNSET` | `UNSET` |
| 2 | `02_JEV_INPUT_QUALITY.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 3 | `03_OPENROUTER_PIPELINE.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 4 | `04_PROCESSING_PERSISTENCE_UI.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 5 | `05_FINAL_VALIDATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |

## Global Guardrails

- Do not remove/bypass JEV or send rejected/manual-review comments to GLM.
- Do not add subjective keyword rejection for toxicity, disagreement, politics, sentiment, or viewpoint.
- Do not weaken consent, credential storage, schema/ID checks, or history compatibility.
- Do not hold Room transactions across network calls.
- Do not raise concurrency without measured evidence.
- Do not log secrets or full comment/prompt/response bodies in production.
- Do not delete/reinterpret historical rows; use versioned configs/schemas and tested migrations.
- Keep commits checkpoint-scoped; no unrelated refactors.

## Blocked Work

If blocked, set the active row to `BLOCKED`, record the exact command/error and smallest required resolution, and stop. Do not start a later checkpoint.

## Completion

The pack is complete only when all five checkpoint rows are `COMPLETE`, each has a commit and evidence, and the final checkpoint records current test/build results.
