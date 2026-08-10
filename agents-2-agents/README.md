# agents-2-agents

Learning exercise: end-to-end **Agent2Agent (A2A)** with Spring Boot and Spring AI Community.

Two Spring Boot apps talk over the A2A protocol — an orchestrator (client) discovers a specialist agent (server) via an Agent Card, sends a natural-language task, and turns the response into a recruiter-facing screening summary.

| | |
| --- | --- |
| **Audience** | Developers learning A2A + Spring AI |
| **Stack** | Java 26 · Spring Boot 4.1 · Spring AI 2.0 · spring-ai-a2a 0.3.0 · A2A Java SDK 0.3.3 |
| **Scope** | One specialist agent + one orchestrator (intentionally small) |

## Quick start (Docker)

```bash
cp .env.example .env          # set OPENROUTER_API_KEY
docker compose up --build
```

```bash
curl -s -X POST http://localhost:8080/api/v1/screenings \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Jane Doe",
    "email": "jane@example.com",
    "jobTitle": "Backend Developer",
    "requiredSkills": "Java, Spring Boot, AWS, Kafka",
    "candidateSkills": "Java, Spring Boot, Azure, Kafka",
    "expectedSalary": 110000
  }' | jq -r '.verdict'
```

## Documentation map

| Doc | What you get |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | System context, components, sequence flow, deployment topology |
| [docs/a2a-protocol.md](docs/a2a-protocol.md) | A2A concepts mapped to classes and HTTP endpoints in this repo |
| [docs/api.md](docs/api.md) | Public REST contract for screening |
| [docs/configuration.md](docs/configuration.md) | Environment variables, YAML properties, Docker networking |
| [docs/runbook.md](docs/runbook.md) | Local JVM / Docker run, health checks, troubleshooting |

## Modules

```text
agents-2-agents/
├── skills-agent/              # A2A server  (:8081, context-path /a2a)
├── screening-orchestrator/    # A2A client + REST (:8080)
├── docker-compose.yml
├── Dockerfile
└── docs/
```

| Module | Role | Port |
| --- | --- | --- |
| `skills-agent` | A2A **server** — `domain` scoring + `tools` + Agent Card | `8081` (`/a2a`) |
| `screening-orchestrator` | A2A **client** — `api` / `application` / `a2a` + REST | `8080` |

## Goals and non-goals

**Goals**

- Show Agent Card discovery, message send, task/artifact response in a real Spring app.
- Keep the specialist logic deterministic (`match-skills`) so scoring is easy to reason about.
- Run the same flow on the host JVM or in Docker Compose.

**Non-goals**

- Production auth, multi-tenant isolation, or observability stacks.
- Streaming A2A, push notifications, or multi-transport setups.
- A full hiring product (salary/background agents are left as stretch work).

## Verify

```bash
export JAVA_HOME="$(jenv prefix 26)"   # if using jenv
mvn verify
```

## Further reading

- [A2A Protocol](https://a2a-protocol.org/)
- [Spring AI Community — spring-ai-a2a](https://github.com/spring-ai-community/spring-ai-a2a)
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/)
