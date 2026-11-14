# Comment Optimization Checkpoint Controller

## Purpose

This file is the sole sequencing and status source of truth for the `comment` optimization. Work only in this repository. Read this file first, then read **only the active checkpoint file**. A checkpoint's STOP rule ends the session.

## Non-Negotiable Product Contract

Preserve YouTube/TXT import, Room history/migrations, WorkManager recovery, encrypted credentials, explicit remote-processing consent, strict schemas and exact IDs, classifications, per-comment summaries, synthesis/evidence navigation, usage/cost metadata, cancellation, leases, and idempotency.

```text
Import -> deterministic local filter -> JEV semantic quality gate
       -> reject / manual review / pass -> batched GLM summaries -> synthesis
```

JEV remains mandatory before GLM. It is an OpenRouter semantic stage, not an offline model. The local filter handles only deterministic high-confidence cases; JEV decides semantic quality for spam, toxic-only, incoherent, irrelevant, or otherwise non-contributing comments. Never reject valuable short comments, questions, criticism, disagreement, negative sentiment, or minority opinions merely for length, tone, sentiment, or viewpoint. Deduplicate only exact source/fingerprint duplicates under a documented, auditable scope—never distinct comments just because their text matches.

## Execution Protocol

1. Confirm worktree state; record the starting commit.
2. Set exactly one row to `IN_PROGRESS`.
3. Read only that checkpoint and the repository areas it names.
4. Perform only its scope and checks.
5. Commit at its checkpoint; record hash/evidence here.
6. Mark it `COMPLETE`, activate the next row, then STOP.

Allowed statuses: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

## Checkpoint 02 Split

Legacy `02_JEV_INPUT_QUALITY.md` is retired and must not be read or executed. Checkpoint 02 is complete only when all three rows are complete:

```text
02A_JEV_INSPECTION.md -> 02B_JEV_IMPLEMENTATION.md -> 02C_JEV_VALIDATION.md
```

This controller overrides any legacy transition that points from Checkpoint 01 directly to the retired file. After Checkpoint 01, activate `02A_JEV_INSPECTION.md`. Checkpoint 03 may start only after 02A, 02B, and 02C are complete.

## Status Ledger

- Starting commit: `UNSET`
- Active checkpoint: `01_BASELINE_AND_MEASUREMENT.md`

| Step | File | Status | Commit | Evidence / blocker |
|---|---|---|---|---|
| 1 | `01_BASELINE_AND_MEASUREMENT.md` | `IN_PROGRESS` | `UNSET` | `UNSET` |
| 2A | `02A_JEV_INSPECTION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 2B | `02B_JEV_IMPLEMENTATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 2C | `02C_JEV_VALIDATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 3 | `03_OPENROUTER_PIPELINE.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 4 | `04_PROCESSING_PERSISTENCE_UI.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 5 | `05_FINAL_VALIDATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |

## Global Guardrails

- Do not remove/bypass JEV or send rejected/manual-review comments to GLM.
- Do not add subjective keyword rejection for toxicity, politics, identity, profanity, criticism, sentiment, disagreement, or viewpoint.
- Do not weaken consent, credential storage/redaction, schema/ID checks, cancellation, leases, idempotency, or history compatibility.
- Do not hold Room transactions across network calls or raise concurrency without evidence.
- Do not log secrets or full comment/prompt/response bodies in production.
- Do not delete/reinterpret historical rows; use versioned configs/schemas and tested migrations.
- Keep commits checkpoint-scoped; no unrelated refactors.

## Blocked Work

If blocked, set the active row to `BLOCKED`, record the exact command/error and smallest required resolution, and STOP. Never skip ahead.

## Completion

The pack is complete only when every row is `COMPLETE`, each row has a commit/evidence entry, and Checkpoint 05 records current validation results.
