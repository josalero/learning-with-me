# token-audit-openrouter

OpenRouter chat-completions adapter for optional semantic review.

## Config

| Env | Purpose |
| --- | --- |
| `OPENROUTER_API_KEY` | Required for live calls (never pass on CLI) |
| `OPENROUTER_MODEL` | Optional model id (default documented in guide) |

## Guide

[docs/guides/semantic-review.md](../../docs/guides/semantic-review.md)

## Non-goals

Evidence selection and audit rules live in `token-audit-llm-reviewer` / core.
