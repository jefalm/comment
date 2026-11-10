# ADR 0004: Retries, Leases, and Exact Earliest-Due Scheduler

## Status
Accepted

## Context
Background processing must survive process termination, handle transient network errors with exponential backoff + full jitter, and avoid duplicate processing via atomic leases.

## Decision
- Implement atomic DAO lease acquisition with `leaseOwner` and `leaseExpiresAt`.
- Classify transient vs permanent HTTP/network errors.
- Schedule retries using `RetryScheduleEntity` tracking the exact minimum `nextAttemptAt` for precise WorkManager wakeups.
