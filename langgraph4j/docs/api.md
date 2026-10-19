# HTTP contract

Base URL: `http://localhost:8093`

Errors use RFC 9457 `ProblemDetail`.

## `GET /api/v1/meta`

Engine identity and `implementerMode` (`STUB`, `CLI`, or leftover `CLOUD`).

## `GET /api/v1/graph`

```json
{ "mermaid": "---\ntitle: Cursor implementation graph\n---\nflowchart TD\n..." }
```

## `GET /api/v1/scenarios`

Ordered lab recipes for the dashboard picker: `interrupt-only`, `approve-ping`, `reject-ping`, `override-prompt`, `default-brief`. Each item has `brief`, optional `autoDecision` (`APPROVE` / `REJECT`), optional `promptOverride`, `expectedStatus`, and `watchFor`.

## `POST /api/v1/scenarios/{id}/run`

Starts that recipe. If `autoDecision` is set, the graph is resumed immediately. Unknown ids are `400`. Recipe semantics and diagrams: [use-cases.md](use-cases.md).

## `POST /api/v1/tasks`

```json
{ "brief": "Return ok from PingService", "startingRef": "main" }
```

Runs `plan` and stops before `approve`. Response includes `threadId`, `status: WAITING_APPROVAL`, and the proposed `prompt`. `repoUrl` is ignored in stub and CLI modes.

## `GET /api/v1/tasks/{threadId}`

Current checkpoint. Unknown ids are `404`.

## `POST /api/v1/tasks/{threadId}/resume`

```json
{ "decision": "APPROVE", "promptOverride": null }
```

`decision` is `APPROVE` or `REJECT`. Optional `promptOverride` replaces the planned prompt before Cursor runs.

Approve with the stub typically returns `DONE` and `verifyPassed: true`. CLI mode copies the seed, runs `agent -p --force` on that workspace, then `./gradlew test`. Reject returns `REJECTED` without calling the implementer.
