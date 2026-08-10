# A2A protocol in this repo

**Audience:** developers who know Spring AI and want to see how A2A maps onto concrete code.  
**External reference:** [A2A Protocol](https://a2a-protocol.org/), [spring-ai-a2a](https://github.com/spring-ai-community/spring-ai-a2a).

## 1. Concepts → this codebase

| A2A concept | Meaning | Where it lives here |
| --- | --- | --- |
| **Agent Card** | JSON metadata: name, description, skills, endpoint URL | Bean in `SkillsAgentApplication`; served at `/.well-known/agent-card.json` |
| **Skill** | Declared capability on the card (discovery hint) | `AgentSkill` `skills_matching` |
| **Message** | Natural-language work request from a client | Built with `A2A.toUserMessage(...)` in `RemoteAgentClient` |
| **Task** | Server-side unit of work created from a message | Created by A2A server / SDK; client observes `TaskEvent` |
| **Artifact** | Result payload (text parts) returned on the task | Extracted from `task.getArtifacts()` in `RemoteAgentClient` |
| **AgentExecutor** | Server hook that runs the agent logic | `DefaultAgentExecutor` + `ChatClient` in `SkillsAgentApplication` |
| **Transport** | How client and server exchange RPC | JSON-RPC via `JSONRPCTransport` |

## 2. Server side (`skills-agent`)

### 2.1 What you provide

Three beans (plus tools):

1. **`AgentCard`** — identity and public URL  
2. **`AgentExecutor`** — how to turn an inbound message into a reply  
3. **`ChatClient`** — Spring AI entry point with tools registered  

Autoconfiguration (`spring-ai-a2a-server-autoconfigure`) wires controllers when `spring.ai.a2a.server.enabled=true`.

### 2.2 Endpoints (context path `/a2a`)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/a2a/.well-known/agent-card.json` | Standard discovery document |
| `GET` | `/a2a/card` | Alternate card path used by some examples |
| `POST` | `/a2a` | JSON-RPC message handling (A2A SDK) |

### 2.3 Executor bridge

```text
A2A Message
  → DefaultAgentExecutor.extractTextFromMessage
  → ChatClient.prompt().user(...).call().content()
  → wrapped as Artifact text for the caller
```

The LLM is instructed to call `match-skills` so the numeric score comes from Java, not from free-form guessing.

### 2.4 Scoring tool contract

`SkillsMatcher.match(candidateSkills, requiredSkills)` (invoked via `SkillsMatcherTools`):

- Normalizes comma-separated lists (trim, lower-case).
- `score = round(100 * matched / required)` (0 if required is empty).
- Verdict bands: `≥75` STRONG, `≥40` PARTIAL, else WEAK.

## 3. Client side (`screening-orchestrator`)

### 3.1 Discovery

On startup, `AgentRegistry` reads `remote.agents.urls` and for each base URL:

```text
GET {base}/.well-known/agent-card.json
```

Cards are keyed by `AgentCard.name()` (for example `"Skills Matcher Agent"`).  
If any URL fails, the application **does not start**.

### 3.2 Sending work

`RemoteAgentClient`:

1. Resolves the card by agent name.  
2. Builds `Client` with `JSONRPCTransport`.  
3. Registers a consumer for `TaskEvent`.  
4. Sends `A2A.toUserMessage(task)`.  
5. Blocks up to 60 seconds for artifact text.

### 3.3 Why the LLM uses a tool

The orchestrator `ChatClient` system prompt lists discovered agents and forbids self-scoring. The only reachability mechanism registered as a tool is:

| Tool name | Parameters | Effect |
| --- | --- | --- |
| `send-message-to-agent` | `agentName`, `task` | Calls `RemoteAgentClient.sendMessage` |

The model chooses the agent name and the natural-language task text. That is the “agentic” part of the client; A2A itself stays transport + discovery.

## 4. End-to-end mental model

```text
REST DTO
  → Orchestrator LLM (plans delegation)
    → A2A Message
      → Skills LLM + match-skills tool
        → Artifact text
  → Orchestrator LLM (writes summary)
→ REST verdict string
```

Two LLM hops are normal: one to decide *what* to ask the remote agent, one inside the remote agent to use the tool and phrase the answer, then the orchestrator may summarize again. Costs and latency scale with that.

## 5. Version pins used here

| Library | Version | Role |
| --- | --- | --- |
| Spring Boot | 4.1.0 | Application platform |
| Spring AI BOM | 2.0.0 | ChatClient / OpenAI-compatible starter → OpenRouter |
| spring-ai-a2a-server-autoconfigure | 0.3.0 | A2A server integration |
| a2a-java-sdk-client | 0.3.3.Final | A2A client SDK |
| Agent Card `protocolVersion` | `0.3.0` | Declared on the card bean |

When upgrading A2A SDK or spring-ai-a2a, re-check Agent Card required fields, JSON-RPC method names, and client builder APIs — they move between releases.

## 6. What this exercise deliberately skips

- Streaming responses (`capabilities.streaming=false`)
- Authentication / Agent Card security schemes
- Client auto-configuration (client is hand-wired with the SDK)
- Agent registries beyond a static YAML URL list
