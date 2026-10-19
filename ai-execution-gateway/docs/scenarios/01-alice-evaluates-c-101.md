# Scenario 1: Alice evaluates C-101

Alice, a recruiter at acme, asks `screening-agent` to evaluate candidate C-101 for requisition R-7. The stub follows a normal screening path. Every tool call is allowed. The candidate does not move until Alice approves the pending request herself.

Run it with `./scripts/run-scenario.sh 1` or the Postman folder **1 Alice evaluates C-101**. The caller is `recruiter-alice` (`DEMO_RECRUITER_API_KEY`).

## Starting point

| Fact | Value |
| --- | --- |
| Tenant | acme |
| Principal | `recruiter-alice`, kind `USER` |
| Scopes | `candidates:read`, `evidence:read`, `transition:request`, `transition:approve` |
| Agent | `screening-agent` |
| Candidate | C-101, status `SCREENING`, requisition R-7 |
| Evidence | E-1, E-2, E-3 |
| Grant | 20 model calls, 20 tool calls, 20,000 tokens, 50,000 micro-USD |

C-101's row stores the canary name `CANARY-NAME-7731` and email `canary7731@example.test`. `get_candidate_summary` does not return them. They do not appear in spans, logs, or any `execution_step` column.

## Workflow

```mermaid
sequenceDiagram
  participant Alice
  participant API
  participant Loop as Orchestrator
  participant Model as Stub
  participant Tools
  Alice->>API: POST /executions C-101
  API-->>Alice: 202 QUEUED
  Loop->>Model: turn 1
  Model-->>Loop: get_candidate_summary
  Loop->>Tools: PERMITTED
  Loop->>Model: turn 2
  Model-->>Loop: search_evidence
  Loop->>Tools: PERMITTED, E-1 E-2 E-3
  Loop->>Model: turn 3
  Model-->>Loop: draft_recommendation ADVANCE
  Loop->>Tools: PERMITTED
  Loop->>Model: turn 4
  Model-->>Loop: request SCREENING to INTERVIEW
  Loop->>Tools: PERMITTED, status stays SCREENING
  Loop->>Model: turn 5
  Model-->>Loop: final answer
  Loop-->>Alice: COMPLETED PASSED
  Alice->>API: POST approve
  API-->>Alice: candidateStatus INTERVIEW
```

1. Alice sends `POST /api/v1/executions` with `agentId=screening-agent`, `taskType=CANDIDATE_EVALUATION`, `candidateId=C-101`, `requisitionId=R-7`, and header `X-API-Key`.
2. The filter accepts the key. The agent exists, the grant is active, and C-101 belongs to acme. The row is saved as `QUEUED`. The response is `202` with `{executionId, traceId, status}`.
3. The worker runs the loop. Each model turn reserves 1 model call, 750 tokens, and 525 micro-USD before the stub answers. Each tool call reserves 1 tool call before authorization.
4. **Turn 1.** The stub calls `get_candidate_summary`. Spring permits it (`candidates:read`) and returns status, requisition, skills, and years of experience. No name and no email.
5. **Turn 2.** The stub calls `search_evidence` with query `skills`. Spring permits it (`evidence:read`) and returns E-1, E-2, and E-3. Those ids are appended to the execution's retrieved evidence.
6. **Turn 3.** The stub calls `draft_recommendation` with recommendation `ADVANCE` and citations E-1 and E-2. Spring permits it and stores the draft. The tool result includes `draftId`. The reconstruction stores only the HMAC of that result.
7. **Turn 4.** The stub calls `request_status_transition` from `SCREENING` to `INTERVIEW`, using the `draftId` from the previous tool result. Spring permits it (`transition:request`). The edge is legal. A `TransitionRequest` is created in `PENDING`. C-101 stays in `SCREENING`.
8. **Turn 5.** The stub returns no tool call. The loop stops.
9. The evaluator sees a terminal `COMPLETED` run, no denied tools, a draft, and citations that are a subset of the retrieved evidence. Verdict `PASSED`, `successful=true`.
10. Alice calls `GET /api/v1/executions/{id}/transition-requests`, then `POST /api/v1/transition-requests/{id}/approve`. That is the only step that sets C-101 to `INTERVIEW`.

## What you should see

| Check | Expected |
| --- | --- |
| Execution status | `COMPLETED` |
| Verdict | `PASSED`, `successful=true` |
| Model calls | 5 |
| Tool calls | 4, all `PERMITTED` |
| Tokens | 3,000 in / 750 out |
| Cost | 2,625 micro-USD |
| Candidate before approve | `SCREENING` |
| Candidate after approve | `INTERVIEW` |

Run this scenario once per database. A second run finds C-101 already in `INTERVIEW`, so the transition request is no longer valid.
