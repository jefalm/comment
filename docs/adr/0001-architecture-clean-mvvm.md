# ADR 0001: Clean MVVM & Provider-Neutral Boundaries

## Status
Accepted

## Context
The application ingests comments from YouTube or local files, processes them through local filtering, JEV 1.14 semantic screening, and GLM 5.3 Flash deep analysis, and presents them in Jetpack Compose UI. We need a decoupled architecture that allows swapping or disabling adapters (YouTube, JEV, GLM) without affecting domain logic or UI.

## Decision
- Adopt Clean Architecture with MVVM.
- Define provider-neutral domain interfaces (`SemanticGate`, `DeepAnalyzer`, `CommentImporter`) so concrete adapters (`OpenRouterJevSemanticGate`, `OpenRouterGlmDeepAnalyzer`, `YouTubeCommentImporter`, `FileCommentImporter`, plus disabled/fake variants) can be injected cleanly.
- Use Room for durable persistence across process death.
- Use WorkManager for reliable background coordination.
