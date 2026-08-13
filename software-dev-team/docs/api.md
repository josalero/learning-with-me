# HTTP API

Base URL: `http://localhost:8095`. Error bodies are RFC 9457 `ProblemDetail` (`title`, `status`, `detail`).

## Dashboard

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/` | Forwards to `/index.html` |

## Catalog

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/teams` | Loaded YAML blueprints |
| POST | `/api/v1/teams` | Create; 409 if id exists |
| PUT | `/api/v1/teams/{id}` | Replace; path id must match body |
| GET | `/api/v1/projects` | Paths relative to `sdlc.home` |
| POST | `/api/v1/projects` | Create |
| PUT | `/api/v1/projects/{id}` | Replace |
| GET | `/api/v1/prompts` | `prompts/*.md` |
| GET | `/api/v1/seeds` | Seed directory ids |
| GET | `/api/v1/conventions` | `docs/conventions/*.md` |
| GET | `/api/v1/role-kinds` | `RoleKind` names for the Teams tab |
| GET | `/api/v1/artifact-files` | Numbered artifact catalog |
| GET | `/api/v1/state-keys` | Agentic-scope keys |
| GET | `/api/v1/status` | `{ offline, llmConfigured, mode }` — `OFFLINE` / `LIVE` / `MISSING_KEY`. Never includes the API key |
| GET | `/api/v1/scenarios` | Ordered Samples-tab recipes (`step`, `track`, `watchFor`, `accent`) |
| POST | `/api/v1/workspace/seed` | Body `{ "projectId": "..." }` → 204 |
| POST | `/api/v1/workspace/reset` | Wipes `workspace/<project>` and recopies the seed → 204 |

## Runs

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/runs` | 202 pending. Body: `teamId`, `projectId`, `featureRequest` |
| GET | `/api/v1/runs` | Newest first |
| GET | `/api/v1/runs/{id}` | One outcome |
| GET | `/api/v1/runs/{id}/events` | SSE `step` events |
| GET | `/api/v1/runs/{id}/artifacts` | File names |
| GET | `/api/v1/runs/{id}/artifacts/{file}` | File body |
| POST | `/api/v1/runs/{id}/approve` | Human stakeholder. Body: `decision`, `reasons`, `followUps` |

## Sample

```bash
curl -sS -X POST http://localhost:8095/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "teamId": "default-scrum-team",
    "projectId": "users-service-java",
    "featureRequest": "Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create."
  }'
```

Sequences: [sequences.md](sequences.md).
