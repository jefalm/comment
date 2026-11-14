# Comment Optimization Checkpoint Controller

## Purpose

This file is the sole sequencing/status source of truth for the `comment` optimization. Work only in this repository. Read this file first, then **only the active checkpoint**. A checkpoint's STOP rule ends the session.

## Non-Negotiable Contract

Preserve YouTube/TXT import, Room history/migrations, WorkManager recovery, encrypted credentials, explicit remote consent, strict schemas/exact IDs, JEV/GLM quality contracts, classifications, per-comment summaries, synthesis/evidence navigation, usage/cost metadata, cancellation, retries, leases, idempotency, and existing UI behavior.

```text
Import -> deterministic local filter -> mandatory JEV semantic gate
       -> reject / manual review / pass -> batched GLM summaries -> synthesis
```

Never route rejected/manual-review comments to GLM. Never weaken validation/security for speed. Do not hold Room transactions across network calls, raise concurrency without baseline evidence, log secrets/full sensitive bodies, or reinterpret historical rows.

## Execution Protocol

1. Record worktree/starting commit.
2. Set exactly one row `IN_PROGRESS`.
3. Read only that file and its explicitly permitted evidence.
4. Perform only its scope/checks.
5. Commit; record hash/evidence.
6. Mark `COMPLETE`, activate the next row, then STOP.

Statuses: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

## Retired Checkpoints and Required Sequences

Do not read/execute legacy `02_JEV_INPUT_QUALITY.md` or `03_OPENROUTER_PIPELINE.md`.

```text
01
 -> 02A_JEV_INSPECTION.md
 -> 02B_JEV_IMPLEMENTATION.md
 -> 02C_JEV_VALIDATION.md
 -> 03A_OPENROUTER_INSPECTION.md
 -> 03B_REQUEST_AND_BATCHING.md
 -> 03C_CONCURRENCY_AND_RESPONSE.md
 -> 03D_PIPELINE_VALIDATION.md
 -> 04
 -> 05
```

Checkpoint 02 is complete only when 02A–02C are complete. Checkpoint 03 is complete only when 03A–03D are complete. This controller overrides legacy transition text in older files.

## Status Ledger

- Starting commit: `7b259d1`
- Active checkpoint: `03B_REQUEST_AND_BATCHING.md`

| Step | File | Status | Commit | Evidence / blocker |
|---|---|---|---|---|
| 1 | `01_BASELINE_AND_MEASUREMENT.md` | `COMPLETE` | `71c7b81` | Baseline 10/100 passed |
| 2A | `02A_JEV_INSPECTION.md` | `COMPLETE` | `68e8bfe` | JEV schema inspection passed |
| 2B | `02B_JEV_IMPLEMENTATION.md` | `COMPLETE` | `7b259d1` | Multi-question JEV implemented |
| 2C | `02C_JEV_VALIDATION.md` | `COMPLETE` | `7b259d1` | JEV validation suite passed |
| 3A | `03A_OPENROUTER_INSPECTION.md` | `COMPLETE` | `PENDING` | `docs/optimization/03A_OPENROUTER_HANDOFF.md` produced |
| 3B | `03B_REQUEST_AND_BATCHING.md` | `IN_PROGRESS` | `UNSET` | `UNSET` |
| 3C | `03C_CONCURRENCY_AND_RESPONSE.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 3D | `03D_PIPELINE_VALIDATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 4 | `04_PROCESSING_PERSISTENCE_UI.md` | `NOT_STARTED` | `UNSET` | `UNSET` |
| 5 | `05_FINAL_VALIDATION.md` | `NOT_STARTED` | `UNSET` | `UNSET` |

## Global Guardrails

- JEV remains mandatory; preserve 02A–02C quality/fairness decisions.
- No subjective keyword filtering or unrelated product changes.
- No destructive migration/history rewrite.
- No concurrency increase without Checkpoint 01 comparison.
- Keep network calls outside Room transactions.
- Keep strict response/ID checks, consent, redaction, cancellation, retry semantics, leases, and idempotency.
- Keep commits micro-checkpoint-scoped.

## Blocked Work

Set the active row `BLOCKED`, record the exact evidence and smallest resolution, then STOP. Never skip ahead.

## Completion

Complete only when every row has `COMPLETE`, commit, and evidence; Checkpoint 05 must record current validation results.
