# ADR 0003: Deduplication v1 & Normalization

## Status
Accepted

## Context
ID-less file imports require robust deduplication within a defined scope key without false positives across scopes.

## Decision
- Define normalization rules (Unicode NFKC, LF newlines, collapsing internal whitespace, preserving case/punctuation/emojis).
- Define fingerprint v1 hashing: SHA-256 over `v1\u001FsourceScopeKey\u001Fparent\u001Fauthor\u001Ftimestamp\u001FnormalizedText`.
- Isolate deduplication within `sourceScopeKey` (`youtube:<videoId>`, `dataset:<id>`, `import:<id>`).
