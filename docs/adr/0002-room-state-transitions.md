# ADR 0002: Room State Machine & Processing Stages

## Status
Accepted

## Context
Comments move through multiple processing stages (Ingestion -> Local Filter -> JEV Gate -> GLM Analysis -> Done) and states (IMPORTED, LOCAL_REJECTED, READY_JEV, JEV_IN_PROGRESS, JEV_REJECTED, READY_GLM, GLM_IN_PROGRESS, COMPLETED, RETRY_WAIT, MANUAL_REVIEW, FAILED_PERMANENT, CANCELLED). State transitions must be atomic, transaction-safe, and resilient to process death.

## Decision
- Store processing state and current stage explicitly in `CommentEntity`.
- Terminals (`LOCAL_REJECTED`, `JEV_REJECTED`, `COMPLETED`, `MANUAL_REVIEW`, `FAILED_PERMANENT`, `CANCELLED`) require explicit configuration version updates for reprocessing.
- Enforce all state changes and lease claims within Room transactions using DAOs.
