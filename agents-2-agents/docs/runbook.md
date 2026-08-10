# Runbook

**Audience:** developers running the exercise locally or in Docker.  
**Related:** [configuration.md](configuration.md), [api.md](api.md).

## 1. Prerequisites

| Requirement | Docker path | Local JVM path |
| --- | --- | --- |
| Docker + Compose | required | optional |
| JDK 26 | build image provides it | required (`jenv shell 26` or `JAVA_HOME`) |
| Maven 3.9+ | build image installs it | required |
| `OPENROUTER_API_KEY` | required | required |

## 2. Docker Compose (recommended)

### Start

```bash
cd agents-2-agents
cp .env.example .env
# edit .env → set OPENROUTER_API_KEY

docker compose up --build
```

Wait until `skills-agent` is healthy and `screening-orchestrator` has started.

### Smoke checks

```bash
# Agent Card from host
curl -fsS http://localhost:8081/a2a/.well-known/agent-card.json | jq '{name, url, skills: [.skills[].id]}'

# Screening
curl -fsS -X POST http://localhost:8080/api/v1/screenings \
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

### Stop

```bash
docker compose down
```

Rebuild after code changes:

```bash
docker compose up --build
```

### Logs

```bash
docker compose logs -f skills-agent
docker compose logs -f screening-orchestrator
```

## 3. Local JVM (two terminals)

Use when iterating on code without rebuilding images.

```bash
export JAVA_HOME="$(jenv prefix 26)"
export PATH="$JAVA_HOME/bin:$PATH"
export OPENROUTER_API_KEY=sk-or-...
```

**Terminal 1 — skills agent first**

```bash
mvn -pl skills-agent spring-boot:run
```

**Terminal 2 — orchestrator**

```bash
mvn -pl screening-orchestrator spring-boot:run
```

Then use the same curl commands as in §2.

## 4. Tests

```bash
export JAVA_HOME="$(jenv prefix 26)"
mvn verify
```

| Module | What is covered |
| --- | --- |
| `skills-agent` | Domain scoring bands and normalization (`SkillsMatcher`) |
| `screening-orchestrator` | Request DTO content; registry URL validation; screening service empty-verdict guard |

LLM / live A2A calls are **not** part of `mvn verify` (no network, no API key required for unit tests).

## 5. Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| Compose: `OPENROUTER_API_KEY is missing a value` | Empty/missing `.env` | Set `OPENROUTER_API_KEY` in `.env` or the shell |
| Orchestrator exits on startup: failed to load agent card | Skills agent not up / wrong URL | Start skills agent first; check `SKILLS_AGENT_URL` |
| Card OK but send fails in Docker | Card `url` still points at `localhost` | Set `A2A_AGENT_PUBLIC_URL=http://skills-agent:8081/a2a/` |
| Screening hangs ~60s then errors | OpenRouter / model issue or remote agent stuck | Check logs; verify key, `OPENROUTER_BASE_URL` includes `/v1`, and model id |
| `400` on screening | Validation | Ensure all fields present; `expectedSalary` > 0 |
| Maven: `release version 26 not supported` | Wrong JDK on `PATH` | `export JAVA_HOME="$(jenv prefix 26)"` before `mvn` |
| Healthcheck never green | App crash-loop (often missing API key) | `docker compose logs skills-agent` |

### Confirm Agent Card URL in Docker

```bash
curl -fsS http://localhost:8081/a2a/.well-known/agent-card.json | jq -r '.url'
# expect: http://skills-agent:8081/a2a/
```

## 6. Direct A2A debugging

Prefer orchestrator for demos. For protocol poking, inspect the card and use the A2A Java SDK client from a scratch main, or POST JSON-RPC to `/a2a` matching the SDK version in use (`0.3.3.Final`). Method names and message envelopes change between releases — treat curl payloads as fragile.

## 7. Acceptance checklist

| # | Criterion | Verification |
| --- | --- | --- |
| AC-1 | Unit tests pass without OpenRouter | `mvn verify` |
| AC-2 | Agent Card is reachable | `GET .../agent-card.json` returns `200` + name `Skills Matcher Agent` |
| AC-3 | Compose brings both services up | `docker compose up --build` healthy + orchestrator listening on `8080` |
| AC-4 | Screening returns a verdict | `POST /api/v1/screenings` → JSON with non-empty `verdict` |
| AC-5 | Skills score is coherent for sample data | Verdict mentions strong/partial match and AWS gap (~75% case) |

## 8. Cleanup

```bash
docker compose down --rmi local   # optional: remove built images
mvn clean                         # local build artifacts
```
