# LangGraph4j orchestrates Cursor

| | |
|---|---|
| **Audience** | Engineers learning LangGraph4j checkpoints and human-in-the-loop |
| **Stack** | Java 26, Spring Boot 4.1.0, LangGraph4j 1.8.26, local Cursor `agent` CLI |
| **Scope** | One graph: plan → approve (interrupt) → Cursor implement → verify |

LangGraph4j owns topology, checkpoints, and resume. The implementer is a local stub (tests) or the Cursor **`agent` CLI** writing `workspace/`. No Cloud Agents and no `CURSOR_REPO_URL`.

## Quick start

```bash
cd langgraph4j
jenv shell 26
./gradlew check
./gradlew bootRun
```

Open [http://localhost:8093](http://localhost:8093). Use the scenario cards to walk interrupt, approve, reject, prompt override, and the empty-brief fallback.

Default `CURSOR_MODE=stub`: the implement node copies `seeds/ping-service-java`, patches `PingService.status()` to `ok`, and runs `./gradlew test`. No Cursor API key required.

## Live Cursor CLI

Needs `agent` on `PATH` (`agent --version`) and a [Cursor API key](https://cursor.com/dashboard?tab=integrations). The agent edits a **copy** of the seed under `workspace/<threadId>/ping-service-java`. Gradle `test` is the verify gate.

```bash
cp .env.example .env   # set CURSOR_API_KEY
export $(grep -v '^#' .env | xargs)
# CURSOR_MODE=cli
./gradlew bootRun
```

`CURSOR_REPO_URL` is not used. That variable was only for Cloud Agents.

## API

```bash
curl -s -X POST http://localhost:8093/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -d '{"brief":"Return ok from PingService"}'

curl -s -X POST http://localhost:8093/api/v1/tasks/<threadId>/resume \
  -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE"}'
```

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/meta` | Engine and implementer mode |
| `GET` | `/api/v1/graph` | Mermaid of the compiled graph |
| `GET` | `/api/v1/scenarios` | Lab recipes for the UI picker |
| `POST` | `/api/v1/scenarios/{id}/run` | Start a recipe and apply its auto-decision |
| `POST` | `/api/v1/tasks` | Plan and interrupt before approve |
| `GET` | `/api/v1/tasks/{id}` | Current checkpointed state |
| `POST` | `/api/v1/tasks/{id}/resume` | Approve / reject, then implement |

## Docs

| Doc | Purpose |
|---|---|
| [docs/README.md](docs/README.md) | Document map |
| [docs/architecture.md](docs/architecture.md) | Graph, ports, seed |
| [docs/api.md](docs/api.md) | HTTP contract |
| [docs/use-cases.md](docs/use-cases.md) | Use cases and diagrams |
| [AGENTS.md](AGENTS.md) | Pointers for coding agents |

## Verify

```bash
jenv shell 26
./gradlew check
```

`./gradlew cursorTest` is reserved for a live Cursor key and is not part of `check`.
