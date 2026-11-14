# Checkpoint 04 — Processing, Persistence, and UI

## Entry Gate

Proceed only if Checkpoints 01–03 are `COMPLETE` and the controller marks this file `IN_PROGRESS`. Read no later checkpoint file.

## Scope

Make retries, leases, large-run continuation, Room access, synthesis, and visible progress correct and efficient. The database becomes the timing/state authority; synthesis occurs only after all eligible analysis reaches a terminal state.

## Relevant Code Areas

- `worker/AnalysisWorker.kt`
- `data/local/AnalysisOrchestrator.kt`
- `data/local/AnalysisPersistenceRepository.kt`
- `domain/retry/RetryPolicy.kt`
- `data/local/dao/CommentDao.kt`
- `data/local/dao/ProcessingBatchDao.kt`
- `data/local/dao/RetryScheduleDao.kt`
- `data/local/entity/RetryScheduleEntity.kt`
- `data/local/entity/CommentEntity.kt`
- `data/local/entity/ProcessingBatchEntity.kt`
- `data/local/AppDatabase.kt`
- `domain/synthesis/EvidencePlanner.kt`
- `domain/synthesis/SynthesisOrchestrator.kt`
- `data/local/AnalysisReadRepository.kt`
- `ui/analysis/AnalysisViewModel.kt`
- `ui/analysis/AnalysisScreen.kt`
- `docs/adr/0004-retries-and-leases.md`

## Required Outcomes

### Retry and continuation

- Add queries for earliest due retry and every unfinished analysis state.
- Capture/honor `Retry-After` from Checkpoint 03 outcomes.
- Schedule one unique coordinator wake-up with `initialDelay`; do not stack persisted delay with `Result.retry()` backoff.
- Express six-hour retry cap as `6L * 60 * 60 * 1000`.
- Recover expired leases exactly once.
- Process bounded item/batch/time slices per Worker invocation.
- Continue imports above 5,000 comments across invocations.

Before synthesis/success, verify no work remains in imported/ready states, due retry batches, planned batches, or recoverable expired leases. Future retries schedule a wake-up; they do not busy-loop.

### Room efficiency and compatibility

- Add/test a composite analysis-selection index: `(importId, processingConfigVersion, processingState, currentStage, nextAttemptAt, localId)`.
- Use minimal planning projections where practical.
- Keep transactions short; bulk-write state/results; never include network I/O.
- Enable/version Room schema export and test every new migration from supported versions.
- Preserve old rows/config meanings and idempotent result uniqueness.

### Synthesis

- Include only terminal JEV-passed/GLM-completed comments.
- Compute totals/category/sentiment distributions in SQL.
- Fetch bounded evidence projections rather than all full comments.
- Preserve deterministic diversity, engagement coverage, existing evidence count/byte/token caps, and strict cited-ID validation.
- Never treat rejected/manual-review/unresolved comments as accepted evidence.

### UI

Expose persisted counts/status for local filter; JEV queued/in-flight/rejected/manual-review/passed; GLM batches completed/total; retry time; permanent failures; and synthesis. Update per committed batch or bounded cadence. Distinguish `WAITING_FOR_RETRY`, `PARTIAL`, `FAILED`, `CANCELLED`, and successful completion. Reconstruct state after process recreation.

## Acceptance Checks

- More than 5,000 comments finish over multiple Worker invocations before synthesis.
- `Retry-After`, earliest-due scheduling, no double backoff, and expired-lease recovery are tested.
- Process death around HTTP/Room boundaries creates no duplicate/lost results.
- `EXPLAIN QUERY PLAN` or equivalent tests confirm critical selectors use intended indexes.
- Migrations preserve historical imports, classifications, summaries, costs, and synthesis.
- Synthesis excludes rejected/unresolved items and cites only bounded evidence IDs.
- UI never reports success while eligible work remains and survives process recreation.
- Run focused Room/Worker/UI tests plus unit/lint/debug build; run relevant instrumentation tests where available.

## Non-Goals / Guardrails

- Do not change quality criteria, model prompts, authority, batching, or concurrency from completed checkpoints except to fix a proven regression.
- Do not delete/rewrite history or use destructive migration.
- Do not put network calls inside transactions.
- Do not update UI per token/item or derive truth only from in-memory state.
- Do not synthesize rejected/manual-review comments.

## Checkpoint / Status Instructions

Record migration versions, scheduling design, scale-test result, process-death/idempotency evidence, and test commands in the controller. Mark `BLOCKED` and stop on any unresolved migration/data-loss risk.

## Commit Checkpoint

Create one focused commit, suggested message:

```text
Complete resilient processing persistence and progress
```

Record its short hash. Mark this step `COMPLETE`, set `05_FINAL_VALIDATION.md` to `IN_PROGRESS`, update `Active checkpoint`, then stop.

## STOP

Do not read or implement `05_FINAL_VALIDATION.md` in this session. Stop after committing and updating the controller.
