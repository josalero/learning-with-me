# UC-3 Reject after plan

**Status:** As-built  
**Catalog id:** `reject-ping`  
**Expected status:** `REJECTED`  
**Duration:** instant

The operator rejects the planned prompt. The graph ends. The implementer is never called.

## Intent

Show that the interrupt is a real gate: reject is a first-class outcome, not a failed implement.

## Actors and trigger

| Actor | Action |
| --- | --- |
| Operator | Runs `reject-ping` or clicks **Reject** on a waiting thread |
| `approve` node | Reads `decision=REJECT`, sets `REJECTED` |
| Conditional edge | Routes to `END` |

`ImplementationPort` is not invoked.

## Preconditions

- Same as UC-1. Implementer mode does not matter because implement does not run.

## Flow

```mermaid
sequenceDiagram
    actor Operator
    participant API as scenarios / tasks API
    participant Orch as TaskOrchestrator
    participant Graph as CompiledGraph
    participant Port as ImplementationPort

    Operator->>API: Run reject-ping
    API->>Orch: start(default brief)
    Orch->>Graph: invoke plan
    Graph-->>Orch: WAITING_APPROVAL
    API->>Orch: resume REJECT
    Orch->>Graph: updateState + resume
    Graph->>Graph: approve rejected
    Graph-->>Operator: REJECTED
    Note over Port: never called
```

```mermaid
flowchart TD
  Start([START]) --> Plan[plan]
  Plan --> Pause{{interrupt after plan}}
  Pause --> Approve[approve]
  Approve -->|REJECT| EndReject([END REJECTED])
  Approve -.->|not taken| Implement[cursor_implement]
  Implement -.-> Verify[verify]

  classDef taken fill:#fecdd3,stroke:#be123c
  class Start,Plan,Pause,Approve,EndReject taken
```

## Resulting state

| Field | Value |
| --- | --- |
| `status` | `REJECTED` |
| `decision` | `REJECT` |
| `prompt` | Unchanged planned prompt |
| `steps` | plan, `approve: human rejected the prompt` |
| `verifyPassed` | `false` |
| Workspace | not created for this thread |

## How to run

```bash
curl -s -X POST http://localhost:8093/api/v1/scenarios/reject-ping/run
```

Or UI card **3. Reject after plan**.

Covered by `TaskApiTest.startThenRejectLeavesCursorUncalled` and `ImplementationGraphTest.rejectEndsWithoutCallingCursor`.

## Acceptance criteria

- Status is `REJECTED`
- Steps include the reject approve line and no `implement:` entry
- Recording / stub port call count stays `0`
- `verifyPassed` stays `false`
