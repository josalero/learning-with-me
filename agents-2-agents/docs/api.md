# API reference

**Audience:** anyone calling the screening HTTP API.  
**Base URL (local/Docker):** `http://localhost:8080`

This module exposes a single public REST endpoint. A2A endpoints on `skills-agent` are for agent clients, not for the recruiter UI.

## 1. Screen a candidate

### `POST /api/v1/screenings`

Runs the orchestrator: delegates skill matching to the remote A2A agent and returns a short prose summary.

**Headers**

| Header | Value |
| --- | --- |
| `Content-Type` | `application/json` |
| `Accept` | `application/json` |

**Request body**

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `name` | string | yes | non-blank |
| `email` | string | yes | non-blank (format not strictly validated) |
| `jobTitle` | string | yes | non-blank |
| `requiredSkills` | string | yes | comma-separated skills |
| `candidateSkills` | string | yes | comma-separated skills |
| `expectedSalary` | number (int) | yes | positive |

**Example request**

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
  }'
```

**Example response** (`200 OK`)

```json
{
  "verdict": "Screening summary for Jane Doe (Backend Developer):\n- Skills: Strong match (~75%). Missing AWS; Azure may transfer.\n- Overall: Good candidate to proceed; follow up on cloud experience."
}
```

The exact `verdict` text is model-generated. The skills score behind it should reflect the deterministic tool when the agent uses `match-skills` correctly.

### Error behaviors

| Situation | Typical result |
| --- | --- |
| Missing / invalid fields | `400` Bean Validation failure (Spring default error body) |
| OpenRouter / remote agent failure mid-call | `502` / `503` via Problem Details (`ScreeningApiExceptionHandler`) |
| Skills agent down at orchestrator startup | Orchestrator process fails to start (not an HTTP error) |

## 2. Skills agent discovery (operators / debugging)

Not part of the recruiter API, but useful for verification.

| Method | URL | Purpose |
| --- | --- | --- |
| `GET` | `http://localhost:8081/a2a/.well-known/agent-card.json` | Fetch Agent Card |
| `GET` | `http://localhost:8081/a2a/card` | Alternate card path |

**Sample card fields to check**

| Field | Expected (default compose) |
| --- | --- |
| `name` | `Skills Matcher Agent` |
| `url` | host: `http://localhost:8081/a2a/` · Docker: `http://skills-agent:8081/a2a/` |
| `skills[].id` | `skills_matching` |

## 3. Direct A2A call (optional)

Prefer the orchestrator for demos. Direct JSON-RPC against `/a2a` is SDK-version sensitive; use it only for protocol debugging. See [runbook.md](runbook.md#direct-a2a-debugging).

## 4. Implementation pointers

| Concern | Type / file |
| --- | --- |
| Request DTO | `api.dto.ScreeningRequest` |
| Response DTO | `api.dto.ScreeningResponse` |
| Controller | `api.ScreeningController` |
| Use case | `application.ScreeningService` |
| Errors | `api.ScreeningApiExceptionHandler` (RFC 9457 Problem Details) |
