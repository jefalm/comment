# ADR 0007: Phased Four-Destination UI Migration

## Status
Accepted

## Context
The current root UI is a single `CommentViewerScreen` that combines YouTube/TXT import and in-memory comment browsing. The approved product direction separates Home, Comments, Analysis, and Settings, while the repository's Room-backed Paging, aggregate analysis, credential, and consent contracts are incomplete or unverified.

## Decision
- Introduce Home, Comments, Analysis, and Settings as the top-level destinations, with Home as the default.
- Preserve the existing viewer, ViewModel, importers, and Room persistence behind a compatibility boundary until replacement behavior is verified.
- Show truthful unavailable states for Comments and Analysis until their Room Paging and analysis aggregate/pipeline prerequisites pass.
- Move YouTube credential management into Settings incrementally; never expose stored plaintext credentials in UI state or prefill entry fields.
- Keep remote processing local-only by default and require explicit consent before any remote analysis path is enabled.
- Treat checked-out code and conclusive tests/builds as implementation evidence; readiness prose and generated artifacts alone do not unlock a destination.

## Consequences
- Navigation can be added without deleting the current import/viewing flow.
- Phase B screens remain gated until their owning backend contracts and tests are verified.
- The current credential dialog must be replaced without restoring saved keys into transient input fields.
