---
name: token-efficiency-auditor
description: >-
  Audit Java AI codebases (Spring AI, LangChain4j) for token waste in prompts,
  tools, RAG, memory, and agents. Use when the user asks for a token audit,
  token efficiency review, LLM cost reduction, or prompt/tool/RAG waste analysis.
---

# Token Efficiency Auditor

Canonical skill for this POC (rules, scripts, report template). A thin installable
pointer also lives in
[agent-skills/skills/token-efficiency-auditor](https://github.com/josalero/agent-skills/tree/main/skills/token-efficiency-auditor)
for catalog/pack install — edit **this** tree when changing audit guidance.

Audit a repository for inefficient AI / LLM usage and produce a structured report.

## When to use

- Token audit, token efficiency, LLM cost reduction
- Spring AI or LangChain4j prompt / tool / RAG / memory review
- Coding-agent context waste analysis

## Workflow

1. **Inspect** AI-related code and configuration:
   - `ChatClient`, `ChatModel`, advisors, tool callbacks
   - Prompt templates, system messages, structured-output schemas
   - Vector store / retrieval `topK` and filters
   - Chat memory implementations
   - Agent loops, handoffs, tool registration
   - Multi-agent topology (workflow, agent, parent, delegation depth)
   - Per-agent and workflow-wide budgets and token attribution
2. **Find** token-heavy execution paths using [rules/](rules/) as checklists.
3. **Estimate** consumption with scripts when helpful:
   - `python3 scripts/count_tokens.py <file-or-dir>`
   - `python3 scripts/detect_duplicates.py <file-or-dir>`
   - `python3 scripts/analyze_tools.py <project-root>`
4. **Suggest** concrete changes (dynamic tools, memory windows, lower `topK`, cheaper routing models, snippet retrieval).
5. **Identify** regression tests needed before applying optimizations (schema validity, tool selection, task success).
6. **Produce** an audit report by filling [templates/audit-report.md](templates/audit-report.md).

## Rules

Read and apply each rule file that matches the codebase:

| Area | File |
| --- | --- |
| Prompts | [rules/prompts.md](rules/prompts.md) |
| RAG | [rules/rag.md](rules/rag.md) |
| Tools | [rules/tools.md](rules/tools.md) |
| Memory | [rules/memory.md](rules/memory.md) |
| Agents | [rules/agents.md](rules/agents.md) |

## Scripts vs judgment

- Prefer **scripts** for token estimates, duplicate blocks, and tool-registration greps.
- Use **judgment** for reachability (which tools a path can use), behavioral risk, and whether an optimization needs an evaluation harness.
- Label estimates as **heuristic** unless runtime telemetry exists.

## Output

Deliver a filled copy of `templates/audit-report.md` (findings, estimates, recommendations, regression tests). Do not claim production savings without runtime data.

## Related engine

Prefer the deterministic CLI when the repo is this project (or any Java tree):

```bash
cd engine
jenv shell 25
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Stable finding IDs: [docs/reference/finding-catalog.md](../../docs/reference/finding-catalog.md).  
Canonical fixture: [examples/spring-ai-support-assistant](../../examples/spring-ai-support-assistant/).

Built-in heuristics cover a small set of high-confidence patterns. Use this skill
for deeper judgment, topology review, estimates without the CLI, and the filled report.

How to verify the POC: [docs/TESTING.md](../../docs/TESTING.md).
