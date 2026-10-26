# Sequences

The HTTP body, the log line, and the trace for one turn are in [turn.md](turn.md). This page is how the three records stay linked while that turn runs.

Three records move together. They are not the same table.

| Record | Table | What a turn does to it |
| --- | --- | --- |
| Session | `agent_session` and `AI_SESSION` | One chat. Same id in both tables. The application row stores the provider, because Spring AI session metadata cannot be updated. |
| Conversation | `AI_SESSION_EVENT` and `tool_approval` | Every message is appended to the event log. A write tool also inserts a `PENDING` approval whose `paused_reply_event_id` is that assistant event. |
| Application | `job_application`, `hiring_action`, `interview_scorecard`, `job_offer` | Unchanged until someone allows the paused tool. The tool names `APP-1` in its arguments. The application is not a child of the session. |

```mermaid
flowchart LR
    subgraph session [Session]
        agentSession[agent_session]
        aiSession[AI_SESSION]
    end
    subgraph conversation [Conversation]
        events[AI_SESSION_EVENT]
        approval[tool_approval]
    end
    subgraph application [Application]
        job[job_application]
        action[hiring_action]
    end

    agentSession ---|"same id"| aiSession
    aiSession --> events
    approval -->|"paused_reply_event_id"| events
    approval -->|"session_id"| agentSession
    action -->|"sessionId:approvalId:toolCallKey"| approval
    action --> job
```

The first diagram is one turn from opening the session through an allowed stage change. The diagram after it is `ApplicationController`: the GET that reads the same application without a session and without the model. The later diagrams are the same turn in the cases the Postman collection runs: deny, a salary above the band, a rule failure, and a replayed decision.

## One turn, three records

Alice opens her session, asks for the stage, asks to schedule the tech screen, then allows it. `APP-1` stays at `RECRUITER_SCREEN` until the allow.

```mermaid
sequenceDiagram
    actor Alice
    participant Sessions as SessionController
    participant Conversation as ConversationController
    participant AppSession as AgentSessionService
    participant Transcript as SessionService
    participant Turn as AgentTurnService
    participant Model as ChatClient
    participant Approval as ApprovalWorkflow
    participant Hiring as HiringService

    Note over Alice, Hiring: Open the session. APP-1 is not touched.
    Alice->>Sessions: POST /api/v1/sessions provider STUB
    Sessions->>AppSession: create
    AppSession->>Transcript: create AI_SESSION
    Transcript-->>AppSession: session id
    AppSession->>AppSession: insert agent_session with that id
    Sessions-->>Alice: 201 id

    Note over Alice, Hiring: Read. The transcript grows. The stage does not.
    Alice->>Conversation: POST /sessions/{id}/messages status of APP-1
    Conversation->>AppSession: requireOwned
    Conversation->>Turn: message
    Turn->>Model: prompt plus SessionAdvisors.session(id)
    Model->>Transcript: append the user message
    Model->>Hiring: getApplication APP-1
    Hiring-->>Model: stage RECRUITER_SCREEN
    Model->>Transcript: append the assistant answer
    Turn-->>Conversation: COMPLETED, no approval
    Conversation-->>Alice: 200

    Note over Alice, Hiring: Pause. The tool call is stored. HiringService is not called.
    Alice->>Conversation: POST /messages schedule tech screen for APP-1
    Conversation->>Turn: message
    Turn->>Model: prompt plus the session advisor
    Model->>Transcript: append the user message
    Model-->>Turn: advanceApplication, confirmation required
    Note over Transcript: assistant event holds the tool call
    Turn->>Approval: insert PENDING, paused_reply_event_id
    Note over Hiring: stage is still RECRUITER_SCREEN
    Conversation-->>Alice: 202 AWAITING_APPROVAL MEDIUM

    Note over Alice, Hiring: Allow. The stored event is replayed. Then the stage moves.
    Alice->>Conversation: POST /approvals/{approvalId}/decision ALLOW
    Conversation->>AppSession: requireVisible
    Conversation->>Approval: decide
    Approval->>Transcript: load the assistant event by paused_reply_event_id
    Approval->>Hiring: advance APP-1 to TECH_SCREEN
    Hiring->>Hiring: insert hiring_action sessionId:approvalId:toolCallKey
    Hiring-->>Approval: stage TECH_SCREEN
    Approval->>Transcript: append the tool result, as the session owner
    Approval-->>Conversation: COMPLETED
    Conversation-->>Alice: 200
```

`POST /messages` is owner-only, so it uses `requireOwned`. A decision uses `requireVisible`: the owner, or a same-tenant approver. Bob can allow Alice's high-risk approval and still gets 404 if he reads her transcript, because `GuardingSessionDecorator` hides `AI_SESSION_EVENT` from anyone who is not the owner. The follow-up model call after an allow runs as the system so that append still lands on Alice's log.

A new message while `tool_approval` is `PENDING` returns 409. The transcript is not given a second turn until that approval is decided.

## The application GET

`ApplicationController` is a second door onto `job_application`. It does not create a session, does not append a message, and does not allow a tool. The lab page and Postman use it to check what the allow actually stored.

There are two reads of the same row:

```mermaid
flowchart LR
    chat[POST /sessions/id/messages] --> model[ChatClient tool]
    model --> hiring[HiringService]
    browser[GET /applications/APP-1] --> controller[ApplicationController]
    controller --> hiring
    hiring --> row[job_application]
```

The chat read happens inside the turn above (`getApplication`). This one is the check after the turn:

```mermaid
sequenceDiagram
    actor Alice
    participant App as ApplicationController
    participant Hiring as HiringService

    Note over Alice, Hiring: Before any allow. No session id is involved.
    Alice->>App: GET /api/v1/applications/APP-1
    App->>Hiring: requireApplication tenant-a, APP-1
    Hiring-->>App: RECRUITER_SCREEN
    App-->>Alice: 200 stage and band

    Note over Alice, Hiring: Schedule is PENDING. The action list is still empty.
    Alice->>App: GET /applications/APP-1/actions
    App->>Hiring: list hiring actions
    Hiring-->>App: none
    App-->>Alice: 200 []

    Note over Alice, Hiring: After the ALLOW in the turn above.
    Alice->>App: GET /api/v1/applications/APP-1
    App->>Hiring: requireApplication tenant-a, APP-1
    Hiring-->>App: TECH_SCREEN
    App-->>Alice: 200

    Alice->>App: GET /applications/APP-1/offer
    App->>Hiring: find offer
    Hiring-->>App: no row
    App-->>Alice: 404 until an extend is allowed
```

Carol calling the same `GET /applications/APP-1` receives 404. The row belongs to tenant-a. The controller never writes the stage. A write still has to come from an allowed tool in the conversation.

## Read the stage

This is the chat path already drawn in the full turn. A status question does not open an approval and does not write a hiring action. The GET in the section above is how you confirm the same stage without asking the model.

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
