# token-audit-llm-reviewer

Provider-neutral second pass: select evidence, redact secrets, call a chat model,
validate structured AI findings, merge as `AI-INFERRED`.

## Purpose

Enrich a deterministic `AuditResult` when the operator opts in (`--llm-review`).

## Non-goals

- Replacing built-in heuristics.
- Owning HTTP / vendor protocols (see `token-audit-openrouter`).
- Sending source without redaction / size caps.

## Guide

[docs/guides/semantic-review.md](../../docs/guides/semantic-review.md)
