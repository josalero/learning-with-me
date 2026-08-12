# Spring AI vs LangChain4j — Research Agents

| | |
|---|---|
| **Audience** | Engineers comparing Spring AI 2.0 and LangChain4j agent orchestration |
| **Stack** | Java 26, Spring Boot 4.1.0, Spring AI 2.0.0, LangChain4j 1.18.1, OpenRouter, Docker Compose |
| **Scope** | Same multi-agent research pipeline behind one UI with an engine toggle |

Two Spring Boot apps implement the identical **planner → researcher → writer/critic** scenario. A static UI lets you run either engine (or both) and watch per-agent steps over SSE.

## Quick start

```bash
cd spring-langchain4j
cp .env.example .env   # set OPENROUTER_API_KEY
jenv shell 26
./gradlew check
docker compose up --build
```

Open [http://localhost:8090](http://localhost:8090).

| Service | Port |
|---|---:|
| UI (nginx) | 8090 |
| Spring AI app | 8091 |
| LangChain4j app | 8092 |

## Documentation map

| Doc | Purpose |
|---|---|
| [docs/README.md](docs/README.md) | Docs index |
| [docs/plan.md](docs/plan.md) | Original implementation plan |
| [docs/architecture.md](docs/architecture.md) | Shared pipeline, packaging, ports |
| [docs/configuration.md](docs/configuration.md) | Env vars, models, prompts |
| [spring-ai-app/docs/architecture.md](spring-ai-app/docs/architecture.md) | Spring AI app internals |
| [langchain4j-app/docs/architecture.md](langchain4j-app/docs/architecture.md) | LangChain4j app internals |

## API (identical on both engines)

```bash
# Spring AI
curl -s http://localhost:8091/api/v1/meta
curl -s -X POST http://localhost:8091/api/v1/research \
  -H 'Content-Type: application/json' \
  -d '{"topic":"Java virtual threads","depth":3}'

# LangChain4j
curl -s http://localhost:8092/api/v1/meta
curl -s -X POST http://localhost:8092/api/v1/research \
  -H 'Content-Type: application/json' \
  -d '{"topic":"Java virtual threads","depth":3}'
```

SSE stream: `GET /api/v1/research/stream?topic=...&depth=3` (events: `step`, `report`).

Errors use RFC 7807 `ProblemDetail` (`ApiExceptionHandler` in each app).

## Engine differences

| Concern | Spring AI 2.0 | LangChain4j 1.18.1 |
|---|---|---|
| Orchestration | Explicit `ResearchOrchestrator` loop | `AgenticServices.sequenceBuilder` + `loopBuilder` |
| Role APIs | Four single-method ports + `ChatClient*Agent` | Four `@Agent` interfaces |
| Structured output | `ChatClient.call().entity(...)` | `@Agent` return types |
| Step events | Internal `StepTrace` + `Consumer<StepEvent>` | `AgentListener` (`agentId` timing) |
| OpenRouter | `spring-ai-starter-model-openai` + `ChatClientConfig` | Manual `OpenAiChatModel` beans |
| Max tokens | `OpenAiChatOptions.maxTokens` via `research.max-tokens` | `OpenAiChatModel.maxTokens` via same property |
| Graph lifecycle | Per-request Java control flow | Agents + review loop as beans; sequence per request |
| System prompts | `shared-prompts/` → `classpath:prompts/` | Same shared files |
| Researcher / writer DTO | `ResearchFindings` | Same `ResearchFindings` (`findingsDoc`) |

Both apps use the same package layers: `api` → `orchestration` → `agents` → `domain`.

## Verify

```bash
jenv shell 26
./gradlew check
```
