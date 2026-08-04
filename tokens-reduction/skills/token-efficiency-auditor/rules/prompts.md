# Prompt rules

Look for patterns that inflate input tokens without improving task success.

## Detect

- **Oversized system prompts** — multi-kilobyte instructions repeated on every call
- **Duplicate instructions** — same rules in system prompt, advisor, and user template
- **Missing token / length limits** — no max input, no truncation policy
- **Expensive models for simple routing** — full-size chat model used only to classify intent
- **Large structured-output schemas** — verbose JSON Schema / Bean definitions sent every request
- **Full documents in the prompt** — entire files or HTML where excerpts would work
- **Unstable prompt prefixes** — dynamic content before a large static block (hurts prompt caching)

## Suggest

- Extract a stable system prefix; put volatile data after it
- Deduplicate overlapping instructions into one source of truth
- Cap prompt sections (history, docs, schemas) with explicit budgets
- Route cheap classifiers to smaller / faster models
- Minimize structured-output schemas to required fields only

## Evidence to collect

- File + method / bean name
- Approximate token estimate (`scripts/count_tokens.py`)
- Whether the prompt is static, per-request, or cached
