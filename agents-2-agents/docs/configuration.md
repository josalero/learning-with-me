# Configuration

**Audience:** developers running or wiring the apps in local or Docker environments.

## 1. LLM provider (OpenRouter)

Both apps use Spring AI’s **OpenAI-compatible** starter pointed at [OpenRouter](https://openrouter.ai/).

| Variable | Required | Default | Notes |
| --- | --- | --- | --- |
| `OPENROUTER_API_KEY` | **yes** | — | From https://openrouter.ai/keys — never commit |
| `OPENROUTER_BASE_URL` | no | `https://openrouter.ai/api/v1` | Must include `/v1` for Spring AI 2 / openai-java SDK |
| `OPENROUTER_CHAT_MODEL` | no | `openai/gpt-4o-mini` | Any OpenRouter model id (e.g. `anthropic/claude-sonnet-4`, `google/gemini-2.0-flash`) |

Mapped properties:

```yaml
spring.ai.openai.api-key: ${OPENROUTER_API_KEY}
spring.ai.openai.base-url: ${OPENROUTER_BASE_URL}
spring.ai.openai.chat.options.model: ${OPENROUTER_CHAT_MODEL}
```

The Maven dependency remains `spring-ai-starter-model-openai` — OpenRouter speaks the OpenAI Chat Completions shape.

## 2. Skills agent (`skills-agent`)

File: `skills-agent/src/main/resources/application.yml`

| Property / env | Default | Purpose |
| --- | --- | --- |
| `server.port` | `8081` | HTTP port |
| `server.servlet.context-path` | `/a2a` | All A2A routes live under this prefix |
| `spring.ai.a2a.server.enabled` | `true` | Enables A2A autoconfiguration |
| `a2a.agent.public-url` / `A2A_AGENT_PUBLIC_URL` | `http://localhost:8081/a2a/` | Written into the Agent Card `url` field — **must be reachable by A2A clients** |

### Why `A2A_AGENT_PUBLIC_URL` matters

Discovery can use one hostname while message send uses the card’s `url`.

| Environment | Recommended `A2A_AGENT_PUBLIC_URL` |
| --- | --- |
| Local JVM | `http://localhost:8081/a2a/` |
| Docker Compose | `http://skills-agent:8081/a2a/` (service name on the `a2a` network) |

If Compose leaves this as `localhost`, the orchestrator container discovers the card successfully but fails when sending messages (it would call itself).

## 3. Screening orchestrator

File: `screening-orchestrator/src/main/resources/application.yml`

| Property / env | Default | Purpose |
| --- | --- | --- |
| `server.port` | `8080` | Public REST port |
| `remote.agents.urls[0]` / `SKILLS_AGENT_URL` | `http://localhost:8081/a2a` | Base URL used to fetch `/.well-known/agent-card.json` |

| Environment | Recommended `SKILLS_AGENT_URL` |
| --- | --- |
| Local JVM | `http://localhost:8081/a2a` |
| Docker Compose | `http://skills-agent:8081/a2a` |

Startup fails if the Agent Card cannot be loaded from every configured URL.

## 4. Docker Compose wiring

`docker-compose.yml` sets:

```text
skills-agent:
  A2A_AGENT_PUBLIC_URL=http://skills-agent:8081/a2a/
  OPENROUTER_API_KEY / OPENROUTER_BASE_URL / OPENROUTER_CHAT_MODEL from host/.env

screening-orchestrator:
  SKILLS_AGENT_URL=http://skills-agent:8081/a2a
  depends_on skills-agent (condition: service_healthy)
```

Healthcheck (image): `GET http://127.0.0.1:8081/a2a/.well-known/agent-card.json` inside the skills container.

Published ports for the host:

| Service | Host URL |
| --- | --- |
| Orchestrator API | http://localhost:8080 |
| Skills Agent Card | http://localhost:8081/a2a/.well-known/agent-card.json |

## 5. Adding a second remote agent

1. Run another A2A server on a new port/service.  
2. Extend YAML:

```yaml
remote:
  agents:
    urls:
      - ${SKILLS_AGENT_URL:http://localhost:8081/a2a}
      - ${SALARY_AGENT_URL:http://localhost:8082/a2a}
```

3. Ensure each card’s `name` is unique — the registry keys by name.  
4. Update the orchestrator system prompt expectations (already injects `describeAgents()`).

## 6. Build-time versions

Parent `pom.xml` properties:

| Property | Value |
| --- | --- |
| `java.version` | `26` |
| `spring-boot` (parent) | `4.1.0` |
| `spring-ai.version` | `2.0.0` |
| `spring-ai-a2a.version` | `0.3.0` |
| `a2a-sdk.version` | `0.3.3.Final` |
