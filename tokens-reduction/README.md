# Token Reduction (POC)

Prove that a small skill + deterministic Java scanner can find common token-waste
patterns in real Spring AI code.

```text
tokens-reduction/
├── skills/token-efficiency-auditor/   # agent skill (edit here)
├── .cursor/skills/, .claude/skills/ → symlink
├── engine/                            # Java 25: core, CLI, optional LLM review
├── examples/spring-ai-support-assistant/
├── docs/                              # finding catalog, guides, testing
├── Makefile                           # make audit | check | demo | scan ...
└── scripts/                           # sh wrappers over the Gradle CLI
```

## What this POC includes

| Piece | Role |
| --- | --- |
| Agent skill | Checklists, helper scripts, audit-report template |
| `token-audit-core` + `token-audit-cli` | Offline scan → five stable finding IDs |
| Example app | Intentional waste fixture + sample report |
| Optional `--llm-review` | OpenRouter second pass (`llm-reviewer` + `openrouter`) |

## What was deliberately left out

MCP server, Spring Boot starter, agent middleware, and the 16 product-form / packaging
exploration docs. Those diluted the demo without proving the audit loop.

## Five-minute demo

Easiest path — from the repo root (`make` handles `jenv` and `cd engine`):

```bash
make audit    # scan the example → five finding IDs
make check    # compile everything + full test suite
make demo     # run the offline app, then audit it
make help     # list every target
```

Scan any other project: `make scan PROJECT=/path/to/app FRAMEWORK=spring-ai`.

`make audit` / `make scan` also write `token-audit-<name>.{md,json,html}` into the
git-ignored `reports/` directory (console uses ANSI severity colors).

Prefer raw Gradle? The equivalent is:

```bash
cd engine
jenv shell 25
./gradlew check
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Expected IDs: `PROMPT-OVERSIZED-SYSTEM`, `TOOLS-ALL-REGISTERED`,
`RAG-EXCESSIVE-TOP-K`, `MEMORY-UNBOUNDED-HISTORY`, `AGENT-OVERSIZED-HANDOFF`.

Full test matrix: **[docs/TESTING.md](docs/TESTING.md)**.

## Docs

| Doc | Purpose |
| --- | --- |
| [docs/TESTING.md](docs/TESTING.md) | How to verify the POC |
| [docs/reference/finding-catalog.md](docs/reference/finding-catalog.md) | Stable finding IDs |
| [docs/guides/running-an-audit.md](docs/guides/running-an-audit.md) | CLI + skill workflow |
| [docs/guides/semantic-review.md](docs/guides/semantic-review.md) | Optional OpenRouter pass |

## Skills

Canonical: `skills/token-efficiency-auditor/`. Cursor/Claude load via symlinks
(`.cursor/skills/`, `.claude/skills/`). Repair with `./scripts/link-skill-targets.sh`.
