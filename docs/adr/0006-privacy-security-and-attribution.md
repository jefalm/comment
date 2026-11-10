# ADR 0006: Privacy, Security, and Optional Attribution

## Status
Accepted

## Context
API keys and user data (comments, authors) must never be logged or leaked. Attribution via `HTTP-Referer` must be optional and absent by default until a canonical repository/app URL exists.

## Decision
- Store API keys in Keystore-backed encrypted storage.
- Sanitize URLs and headers in OkHttp logging; never log API keys, query strings, request bodies, comment bodies, or author data in release builds.
- `HTTP-Referer` is absent by default and omitted from OpenRouter requests until explicitly configured with a valid absolute HTTPS URL.
