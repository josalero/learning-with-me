# AI SDLC Agent Team (Cursor CLI)

Six AI roles run a bounded software-delivery loop against a **real local git repo** and a **real build**. Team composition and technology are YAML, not Java.

This is a sibling of [`../software-dev-team`](../software-dev-team). The pipeline is the same; live chat goes through the Cursor **`agent` CLI** instead of OpenRouter HTTP.

| Piece | Choice |
| --- | --- |
| Language | Java 26 |
| App | Spring Boot 4.1 |
| Agents | LangChain4j 1.18.1 + `langchain4j-agentic` 1.18.1-beta28 |
| Build | Gradle Groovy |
| LLM | Cursor CLI (`agent -p --mode ask`), or `SDLC_OFFLINE=true` |
| HTTP | Port **8096** (8095 stays with the OpenRouter lab) |

## Docs

| Doc | What it answers |
| --- | --- |
| [docs/how-to.md](docs/how-to.md) | Run it, start a feature, add a team or stack |
| [docs/api.md](docs/api.md) | HTTP contract |
| [docs/architecture.md](docs/architecture.md) | Modules, pipeline, loops, safety |
| [docs/agent-state.md](docs/agent-state.md) | Who writes which agentic-scope key |
| [docs/sequences.md](docs/sequences.md) | Sequence diagrams |
| [docs/sample-specs/README.md](docs/sample-specs/README.md) | Example Feature Brief and AI Spec |
| [docs/roles.md](docs/roles.md) | What each role produces |
| [docs/loops.md](docs/loops.md) | Rework caps and exit conditions |
| [docs/adding-a-team.md](docs/adding-a-team.md) | New team YAML and new technology profile |
| [docs/adding-a-role.md](docs/adding-a-role.md) | New `RoleKind` (Java) |
| [AGENTS.md](AGENTS.md) | Pointers for coding agents |

## Quick start

Offline walkthrough (no API key). Canned role output; seed tests still run.

```bash
cd software-dev-team-cursor
jenv shell 26
export SDLC_OFFLINE=true
./gradlew check
./scripts/seed-workspace.sh users-service-java
./gradlew :sdlc-app:bootRun
```

Open **http://localhost:8096/** — Feature, Teams, Projects, Results.

Live LLM runs: install the Cursor CLI (`agent --version`), copy `.env.example` to `.env`, set `CURSOR_API_KEY`, leave `SDLC_OFFLINE=false`.

```bash
export $(grep -v '^#' .env | xargs)
./gradlew :sdlc-app:bootRun
```

The CLI is invoked as `agent -p --mode ask` so it cannot write the workspace. Developer file edits still go through LangChain4j tools.

Docker runs both modes. The runtime image installs the Linux Cursor CLI, so live runs work in Compose as long as `CURSOR_API_KEY` is set and `SDLC_CURSOR_CLI` stays `agent` (a host path breaks the container).

```bash
cp .env.example .env
SDLC_OFFLINE=true docker compose up --build         # offline walkthrough
docker compose up --build                           # live, reads CURSOR_API_KEY from .env
```

A full walkthrough is in [docs/how-to.md](docs/how-to.md).
