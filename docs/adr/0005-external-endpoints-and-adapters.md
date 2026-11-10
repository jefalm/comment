# ADR 0005: External Endpoints, Models, and Adapters

## Status
Accepted

## Context
The app integrates with YouTube Data API v3, OpenRouter JEV 1.14 (`/api/v1/systemone`), and OpenRouter GLM 5.3 Flash (`/api/v1/chat/completions` with strict JSON schema). Adapters must start disabled by default.

## Decision
- YouTube live adapter, JEV live adapter, and GLM live adapter start disabled.
- JEV uses `/api/v1/systemone` with model `typesafe/jev-1.14`.
- GLM uses `/api/v1/chat/completions` with model `z-ai/glm-5.3-flash`, strict json_schema, `require_parameters = true`, and reasoning effort low/exclude.
