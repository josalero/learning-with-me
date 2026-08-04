# Spring AI support assistant (audit fixture)

A compiling customer-support service built on the **real Spring AI 2.0.0** API
(`spring-ai-client-chat`, `spring-ai-vector-store`) — `ChatClient`,
`defaultSystem` / `defaultTools`, `@Tool`, `VectorStore`, `SearchRequest.topK`.
Only the model is an offline `ChatModel` test double (`OfflineChatModel`) so the
demo runs deterministically without a provider API key or network.

This is **not** a toy named `WastefulAgent`. It looks like something a team
would ship — and the waste comes from ordinary product decisions:

| Decision | Where | Finding ID |
| --- | --- | --- |
| One evergreen system prompt for every turn | `SupportAssistantConfig` | `PROMPT-OVERSIZED-SYSTEM` |
| One tool bean with user + privileged tools | `SupportTools` → `defaultTools(...)` | `TOOLS-ALL-REGISTERED` |
| Fixed `topK(20)`, no similarity threshold | `SupportAssistantService` | `RAG-EXCESSIVE-TOP-K` |
| Append-only `List<Message>` replayed every turn | `SupportAssistantService` | `MEMORY-UNBOUNDED-HISTORY` |
| Escalation forwards the entire transcript | `EscalationCoordinator` | `AGENT-OVERSIZED-HANDOFF` |

## Layout

```text
com.example.support
├── SupportApplication          # offline main
├── SupportAssistantConfig      # ChatClient.builder defaults
├── SupportAssistantService     # RAG + memory
├── SupportTools                # @Tool methods
├── EscalationCoordinator       # parent → child handoff
├── SpecialistAgent             # child agent
├── InMemoryVectorStore         # real VectorStore, in-memory
└── OfflineChatModel            # real ChatModel, offline test double
```

## Run offline

```bash
cd engine
jenv shell 25
./gradlew :spring-ai-support-assistant:run
```

## Audit it

```bash
cd engine
./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai'
```

Expected: the five finding IDs above. Sample filled report:
[audit-report.md](audit-report.md). Full verification steps:
[docs/TESTING.md](../../docs/TESTING.md).

Optional OpenRouter semantic pass (see [semantic review guide](../../docs/guides/semantic-review.md)):

```bash
OPENROUTER_API_KEY='your-key' ./gradlew :token-audit-cli:run \
  --args='scan ../examples/spring-ai-support-assistant --framework spring-ai --llm-review'
```
