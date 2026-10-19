# Agent instructions — software-dev-team-cursor

Java **26**, Spring Boot **4.1**, LangChain4j **1.18.1**, Gradle Groovy.

This folder is a sibling of `software-dev-team`. Same six-role pipeline; live LLM calls go through the Cursor **`agent` CLI**. Planning roles use `-p --mode ask`. L2 (Developer) writes the seeded `workspace/` with a local agent run (`-p --force`, no Cloud Agents).

## Layout

| Path | Role |
| --- | --- |
| `sdlc-core/` | Domain records, catalogs, ports. No LangChain4j, no Spring. |
| `sdlc-app/` | Agents, orchestration, adapters, HTTP. |
| `config/teams/` | Who is on the team. |
| `config/projects/` | Technology + allowlisted commands. |
| `seeds/` | Playground services the team edits after copy to `workspace/`. |
| `docs/` | Architecture, sequences, how-to, sample specs. |

## Docs to read first

- [docs/how-to.md](docs/how-to.md)
- [docs/agent-state.md](docs/agent-state.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/sequences.md](docs/sequences.md)

## Commands

```bash
jenv shell 26
./gradlew check
./gradlew llmTest          # needs CURSOR_API_KEY and `agent` on PATH
./scripts/seed-workspace.sh users-service-java
SDLC_OFFLINE=true docker compose up --build
```

Do not put business logic for users-CRUD in `sdlc-app`. That lives in seeds.

Public Java types need Javadoc (what / when / sample). Checkstyle `MissingJavadocType` is part of `check`.

New role kinds: [docs/adding-a-role.md](docs/adding-a-role.md). Pipeline entry: `SdlcOrchestrator` → `SdlcPipelineFactory`.

Live models: `CursorCliChatModel` stays `--mode ask` with no `--force`. File writes for L2 and review/QA rework go through `LocalCursorDeveloper` on the project workspace. Offline (`SDLC_OFFLINE=true`) still uses LangChain4j `DeveloperTools` so `./gradlew check` never starts `agent`. Set `SDLC_DEVELOPER_IMPLEMENTER=TOOLS` to keep live ask-mode + tools.

Live runs work on the host (`agent` on `PATH`) and in Compose (Linux CLI installed by `docker/Dockerfile`). Keep `SDLC_CURSOR_CLI=agent`; an absolute host path breaks the container.
