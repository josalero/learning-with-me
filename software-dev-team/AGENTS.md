# Agent instructions — software-dev-team

Java **26**, Spring Boot **4.1**, LangChain4j **1.18.1**, Gradle Groovy.

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
./gradlew llmTest          # needs OPENROUTER_API_KEY
./scripts/seed-workspace.sh users-service-java
SDLC_OFFLINE=true docker compose up --build
```

Do not put business logic for users-CRUD in `sdlc-app`. That lives in seeds.

Public Java types need Javadoc (what / when / sample). Checkstyle `MissingJavadocType` is part of `check`.

New role kinds: [docs/adding-a-role.md](docs/adding-a-role.md). Pipeline entry: `SdlcOrchestrator` → `SdlcPipelineFactory`.
