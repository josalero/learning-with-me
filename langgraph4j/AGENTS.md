# Agent instructions — langgraph4j POC

Java **26**, Spring Boot **4.1**, LangGraph4j **1.8.26**, Gradle Groovy.

## Layout

| Path | Role |
| --- | --- |
| `src/main/java/.../api/` | REST, lab scenario catalog, static UI at `/` |
| `src/main/java/.../domain/` | Brief planner, statuses, implementer DTOs |
| `src/main/java/.../graph/` | `StateGraph`, nodes, orchestrator |
| `src/main/java/.../cursor/` | `ImplementationPort`, stub, local `agent` CLI (`CliCursorImplementer`) |
| `seeds/ping-service-java/` | Failing acceptance test the implementer must fix |
| `docs/` | Architecture, API, and [use-cases](docs/use-cases.md) |

## Invariants

- LangGraph4j owns plan, interrupt, resume, retry. Cursor does not orchestrate.
- `./gradlew check` must use the stub. Never call Cursor from unit tests.
- Do not log `CURSOR_API_KEY` or Cursor tokens.
- Live implementer is `CURSOR_MODE=cli` (`agent -p --force` on `workspace/`). `CURSOR_REPO_URL` is unused.
- Lab scenarios live in `LabScenarioCatalog`. Do not invent extra HTTP verbs for stub vs CLI; mode is server config.

```bash
jenv shell 26
./gradlew check
./gradlew bootRun
```
