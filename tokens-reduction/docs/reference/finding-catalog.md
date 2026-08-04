# Finding catalog

Stable finding IDs emitted by `token-audit-core` built-in analyzers.
Treat IDs as public API: do not rename without a migration note.

## Current detectors

| ID | Severity | Area | Source shapes | Recommendation summary |
| --- | --- | --- | --- | --- |
| `PROMPT-OVERSIZED-SYSTEM` | Medium | prompts | Large text block in a `*SYSTEM*/*PROMPT*` field, or inline `defaultSystem("""...""")` / `system("""...""")` (≥ 800 chars) | Shorten the stable prefix; load task-specific policy on demand |
| `TOOLS-ALL-REGISTERED` | High | tools | ≥ 5 line-anchored `@Tool` methods in one class, or `.defaultTools(allTools)` / `.tools(allTools)` | Select tools by intent; keep privileged tools off general paths |
| `RAG-EXCESSIVE-TOP-K` | Medium | rag | `.topK(n)` with `n > 8` | Start near `topK=4` plus a similarity threshold; evaluate recall |
| `MEMORY-UNBOUNDED-HISTORY` | High | memory | `List<…> conversation|history|transcript|messages|memory = new ArrayList` | Use token/message-window memory with summarize-and-evict |
| `AGENT-OVERSIZED-HANDOFF` | High | agents | `.handoff|delegate|escalate|…(…conversation|transcript|history…)` | Pass a task-scoped brief, not the full parent context |

## Canonical fixture

[examples/spring-ai-support-assistant](../../examples/spring-ai-support-assistant/) must produce exactly these five IDs when scanned with `--framework spring-ai`.
Regression coverage: `SpringAiSupportAssistantScanTest`.

## Heuristic limits

- Detectors are regex / source-shape based, not full AST or CFG analysis.
- Javadoc mentions of `@Tool` are ignored (line-anchored match only).
- Token estimates use a chars/4 heuristic unless a real tokenizer is wired.
- Runtime waste (retries, unused retrieved chunks, live tool-schema size) is out of
  scope for this POC (no runtime instrumentation).

## Optional AI findings

With `--llm-review`, additional findings may appear with origin `AI-INFERRED`.
Those IDs are not part of this catalog until promoted to deterministic detectors.
See [guides/semantic-review.md](../guides/semantic-review.md).
