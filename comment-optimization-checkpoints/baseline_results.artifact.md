# Baseline and Measurement Report (Checkpoint 01)

## Overview

This report documents the synthetic baseline execution, telemetry capture, and error recovery behaviors for the comment analysis pipeline (Local Filter -> JEV Semantic Gate -> GLM Deep Analysis -> Synthesis) validated via unit tests (`AnalysisBaselineTest`).

## Scenarios & Synthetic MockWebServer Measurements

| Scenario | Comments | HTTP Status / Condition | JEV Requests | GLM Requests | Measurement Type | Execution Result | Tokens / Cost |
|---|---|---|---|---|---|---|---|
| Baseline 10 | 10 | 200 OK | 10 | 1 (batched) | Synthetic (MockWebServer) | Passed | Verified via telemetry |
| Baseline 100 | 100 | 200 OK | 100 | 5 (batched) | Synthetic (MockWebServer) | Passed | Verified via telemetry |
| Rate Limit (429) | 1 | 429 Too Many Requests | 1 | 0 | Synthetic (MockWebServer) | Passed (Retry scheduled) | 0 tokens / $0.0 |
| Server Error (5xx) | 1 | 503 Service Unavailable | 1 | 0 | Synthetic (MockWebServer) | Passed (Retry scheduled) | 0 tokens / $0.0 |
| Malformed JSON | 1 | 200 OK (Invalid Body) | 1 | 0 | Synthetic (MockWebServer) | Passed (Retry scheduled) | 0 tokens / $0.0 |

## Telemetry & Guardrails Verification

- **Production Safety**: Telemetry recorder and instrumentation record counts, latencies, byte counts, and token/cost metadata without logging API keys, Authorization headers, full comment text, prompts, or full response bodies.
- **Error Handling**: 429 and 5xx errors correctly trigger retry scheduling (`ProcessingState.RETRY_WAIT`). Malformed JSON and validation failures correctly transition to retry wait / failure states.
- **Unit Test Verification**: `testDebugUnitTest` executed successfully (137 passed, 0 failed).
