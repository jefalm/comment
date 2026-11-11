# ADR 0005: External Endpoints, Models, and Adapters

## Status
Accepted

## Context
The app integrates with YouTube Data API v3, OpenRouter JEV 1.14 (`POST /api/alpha/decisions`), and OpenRouter GLM 5.3 Flash (`/api/v1/chat/completions` with strict JSON schema). Adapters must start disabled by default.

## Decision
- YouTube live adapter, JEV live adapter, and GLM live adapter start disabled.
- JEV uses `POST /api/alpha/decisions` with model `typesafe/jev-1.14`.
  - Native Android Retrofit adapter: `POST /api/alpha/decisions`
  - TypeSafe SDK compatibility: `POST /api/v1/systemone`
  - Both use the Jev/System One family, but this app directly targets Decisions.
- GLM uses `/api/v1/chat/completions` with model `z-ai/glm-5.3-flash`, strict json_schema, `require_parameters = true`, and reasoning effort low/exclude.
