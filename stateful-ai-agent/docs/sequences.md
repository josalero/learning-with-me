# Sequences

`postman/Stateful-hiring-agent.postman_collection.json` runs the first five diagrams in order on a fresh `APP-1`. The stub phrases in `http/scenarios.http` and `scripts/walkthrough.py` follow the same funnel.

## Read the stage

A status question does not open an approval and does not write a hiring action.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Turn as AgentTurnService
    participant Model
    participant Hiring as HiringService

    Alice->>API: POST /messages status of APP-1
    API->>Turn: message
    Turn->>Model: prompt and tools
    Model-->>Turn: getApplication
    Turn->>Hiring: read stage
    Hiring-->>Turn: RECRUITER_SCREEN
    API-->>Alice: 200 COMPLETED, no approval
```

## Schedule, then allow

The model asks to move the stage. The turn stores `PENDING` and returns `202`. The stage changes only after Alice allows the call.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Turn as AgentTurnService
    participant Model
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: POST /messages schedule tech screen
    API->>Turn: message
    Turn->>Model: prompt and tools
    Model-->>Turn: advanceApplication TECH_SCREEN
    Turn->>Approval: insert PENDING
    Note over Hiring: hiring_action is still empty
    API-->>Alice: 202 AWAITING_APPROVAL MEDIUM

    Alice->>API: POST /decision ALLOW plus Idempotency-Key
    API->>Approval: decide
    Approval->>Hiring: advance to TECH_SCREEN
    Hiring-->>Approval: one hiring_action row
    API-->>Alice: 200 COMPLETED
```

Bob uses the same pause on his own session for `ONSITE` and `OFFER`. Those calls are `HIGH` because he is the approver. Acceptance then sets the offer to `ACCEPTED` and the stage to `HIRED`.

## Deny

A deny closes the pause and leaves the application where it was. The collection denies the first schedule, then sends a second one.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: POST /messages schedule tech screen
    API-->>Alice: 202 PENDING advanceApplication
    Alice->>API: POST /decision DENY reason Not now
    API->>Approval: decide
    Note over Approval: PENDING becomes DENIED
    Note over Hiring: stage stays RECRUITER_SCREEN
    API-->>Alice: 200 DENIED
```

## Above the band

`190000` is above `APP-1`'s band, so the pause is `HIGH`. Alice can see the approval and still cannot allow it. Bob allows it without being able to read her session.

```mermaid
sequenceDiagram
    actor Alice
    actor Bob
    participant API
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: POST /messages extend L4 190000
    API-->>Alice: 202 HIGH extendOffer
    Alice->>API: POST /decision ALLOW
    API-->>Alice: 403 PENDING stays open
    Bob->>API: GET /sessions/{aliceSessionId}
    API-->>Bob: 404
    Bob->>API: POST /decision ALLOW new Idempotency-Key
    API->>Approval: decide
    Approval->>Hiring: insert offer EXTENDED
    API-->>Bob: 200 COMPLETED
```

Carol and Dave also receive `404` on Alice's session. A tenant mismatch looks like a missing session.

## Rule failure after allow

The pause is created before the band or the scorecard is checked. Allow runs the tool, and the hiring rule ends the approval as `FAILED`.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: POST /messages extend L4 90000
    API-->>Alice: 202 MEDIUM extendOffer
    Alice->>API: POST /decision ALLOW
    API->>Approval: decide
    Approval->>Hiring: extendOffer
    Hiring-->>Approval: OFFER_OUT_OF_BAND
    Note over Hiring: no offer row, stage stays OFFER
    API-->>Alice: 200 FAILED
```

An onsite `NO` uses the same shape. Bob allows the advance to `OFFER`, `HiringService` returns `SCORECARD_NOT_PASSING`, and the stage stays `ONSITE`. A later `STRONG_YES` is the card the gate uses, so the next advance completes.

## Same decision twice

The first key writes the action. The same key returns that result. A different key after the row is decided returns `409`.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: ALLOW Idempotency-Key K
    API->>Approval: lock row, status EXECUTING
    Approval->>Hiring: insert hiring_action once
    API-->>Alice: 200 COMPLETED
    Alice->>API: ALLOW Idempotency-Key K
    API->>Approval: lock, see stored result
    API-->>Alice: 200 same answer
    Alice->>API: ALLOW Idempotency-Key K2
    API-->>Alice: 409
```

## Restart while pending

The pause is the approval row and the stored assistant message. A new process loads both and runs the tool once.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant DB

    Alice->>API: POST /messages schedule tech screen
    API->>DB: PENDING and stored reply
    API-->>Alice: 202
    Note over API: process stops
    Alice->>API: POST /decision ALLOW on the new process
    API->>DB: load approval and reply, check argument hash
    API->>DB: insert hiring_action once
    API-->>Alice: 200 COMPLETED
```

`RestartWhilePendingScenarioTest` closes the first Spring context and approves from a second one on the same database.

## Not on the collection run

### Retry after a forced failure

`FailureGate` fails the next scorecard write and records `FAILED`. Retry uses the same idempotency key, so the second attempt inserts the scorecard once.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Alice->>API: ALLOW scorecard
    Approval->>Hiring: write fails
    API-->>Alice: 200 FAILED
    Alice->>API: POST /approvals/{id}/retry
    API->>Approval: same idempotency key
    Approval->>Hiring: insert scorecard once
    API-->>Alice: 200 COMPLETED
```

### Provider switch

A `STUB_FOREIGN` turn stores an opaque part. Patching the session to `STUB` with `REJECT` makes the next message `422`. `STRIP_PROVIDER_PAYLOAD` rewrites that part to text before the stub sees the prompt. The event log still has the original part.

```mermaid
sequenceDiagram
    actor Alice
    participant API
    participant Turn as AgentTurnService

    Alice->>API: POST /sessions provider STUB_FOREIGN
    Alice->>API: POST /messages hello
    API-->>Alice: 200 part provider stub-foreign
    Alice->>API: PATCH provider STUB mode REJECT
    Alice->>API: POST /messages note after switch
    API->>Turn: active messages contain a foreign part
    API-->>Alice: 422 provider-incompatible
```

### Compaction

With the test limit of 3 turns, the fourth `note:` message stores a compaction event. `view=all` keeps the full log. `view=active` is the window the model sees. Compose keeps 20 turns, so four notes do not compact there.
