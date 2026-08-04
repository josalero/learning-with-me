# How to test this POC

Prerequisites: JDK **25** via `jenv`, Python 3 (skill helper scripts only).

## Shortcuts (from repo root)

`make` wraps every step below (it activates `jenv` and runs from `engine/`):

| Command | Equivalent |
| --- | --- |
| `make check` | full build + test suite (§1) |
| `make test` | force-rerun the scan regression test (§1) |
| `make audit` | scan the canonical example (§2) |
| `make scan PROJECT=<path> FRAMEWORK=<name>` | scan any project (§2) |
| `make demo` | run the offline app, then audit it (§3) |
| `make tokens` / `make tools` | skill helper scripts (§4) |
| `make review` | OpenRouter semantic pass, needs `OPENROUTER_API_KEY` (§5) |

The raw Gradle commands below still work if you prefer them:

```bash
cd engine
jenv shell 25
```

## 1. Automated suite (required)

Compiles all modules and runs unit + fixture tests (offline, no API key):

```bash
./gradlew check
```

What this covers:

| Module | What is asserted |
| --- | --- |
| `token-audit-core` | Analyzer SPI wiring; scan of `examples/spring-ai-support-assistant` yields the five catalog finding IDs |
| `token-audit-cli` | `scan` exit codes and summary output; unknown framework / provider rejected |
| `token-audit-llm-reviewer` | Evidence collection, redaction, merge of mocked AI findings |
| `token-audit-openrouter` | Request shaping / config (no live network in unit tests) |
| `spring-ai-support-assistant` | Example compiles against real Spring AI 2.0.0 (`spring-ai-client-chat`, `spring-ai-vector-store`) |

Focused core fixture test only:

```bash
./gradlew :token-audit-core:test --tests '*SpringAiSupportAssistantScanTest'
```

## 2. Manual CLI demo (required smoke)

```bash
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

**Pass criteria:** exit 0 and output includes all of:

- `PROMPT-OVERSIZED-SYSTEM`
- `TOOLS-ALL-REGISTERED`
- `RAG-EXCESSIVE-TOP-K`
- `MEMORY-UNBOUNDED-HISTORY`
- `AGENT-OVERSIZED-HANDOFF`

IDs are documented in [reference/finding-catalog.md](reference/finding-catalog.md).

`make audit` (and `make scan`) additionally write `reports/token-audit-<name>.md`
and `.json` (git-ignored). To write a report from the raw CLI, add repeatable
`--out <file>` options; format is inferred from the extension or forced with
`--format text|json|md`.

## 3. Offline example app (optional smoke)

```bash
./gradlew :spring-ai-support-assistant:run
```

**Pass criteria:** process starts, prints a short multi-turn support conversation, exits without error.
It uses the real Spring AI `ChatClient` with an offline `ChatModel` test double
(`OfflineChatModel`), so no provider API key or network is required.

## 4. Skill helper scripts (optional)

```bash
cd ..   # repo root
python3 skills/token-efficiency-auditor/scripts/count_tokens.py \
  examples/spring-ai-support-assistant/src
python3 skills/token-efficiency-auditor/scripts/analyze_tools.py \
  examples/spring-ai-support-assistant
```

**Pass criteria:** scripts exit 0 and print heuristic counts (not exact tokenizer bills).

## 5. Optional OpenRouter semantic review (live)

Needs a real key; **not** part of `./gradlew check`.

```bash
cd engine
OPENROUTER_API_KEY='your-key' ./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai --llm-review'
```

**Pass criteria:** deterministic findings still present; any AI rows are labeled `AI-INFERRED`;
provider usage is printed. See [guides/semantic-review.md](guides/semantic-review.md).

## 6. Agent skill (manual)

In Cursor / Claude / Codex with this repo open, ask for a token audit of
`examples/spring-ai-support-assistant`. Prefer running the CLI first, then filling
[skills/token-efficiency-auditor/templates/audit-report.md](../skills/token-efficiency-auditor/templates/audit-report.md).

Compare against the sample report:
[examples/spring-ai-support-assistant/audit-report.md](../examples/spring-ai-support-assistant/audit-report.md).

## Out of scope for this POC

No MCP server, Spring Boot starter, runtime middleware, HTML reports, or evaluation
replay. Those were exploratory skeletons and were removed to keep the POC focused.
