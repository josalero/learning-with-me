# Configuration

Copy [`.env.example`](../.env.example) to `.env` before `docker compose up`.

| Variable | Default | Used by |
|---|---|---|
| `OPENROUTER_API_KEY` | *(required)* | Both apps |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | Both apps |
| `OPENROUTER_CHAT_MODEL` | `openai/gpt-4o-mini` | Planner, writer, critic |
| `OPENROUTER_RESEARCH_MODEL` | `openai/gpt-4o-mini:online` | Researcher (web search) |
| `OPENROUTER_MAX_TOKENS` | `4096` | Max completion tokens for every model call (both apps) |

## Spring AI mapping

```yaml
spring.ai.openai.api-key: ${OPENROUTER_API_KEY}
spring.ai.openai.base-url: ${OPENROUTER_BASE_URL}
spring.ai.openai.chat.options.model: ${OPENROUTER_CHAT_MODEL}
```

`ChatClientConfig` builds both clients with `OpenAiChatOptions.maxTokens` from `research.max-tokens`, and sets `researchChatClient` model → `OPENROUTER_RESEARCH_MODEL`.

## LangChain4j mapping

Manual beans in `Langchain4jConfig`:

- `chatModel` → `OPENROUTER_CHAT_MODEL`
- `researchChatModel` → `OPENROUTER_RESEARCH_MODEL`
- both → `temperature` from `research.temperature`, `maxTokens` from `research.max-tokens`

Bound via `research.openrouter.*` and `research.*` properties. Agents + review loop are beans in `ResearchPipelineConfig`.

## Orchestration knobs

| Property | Default | Meaning |
|---|---:|---|
| `research.pass-threshold` | 9 | Critic score that ends the loop (`score >= 9`, i.e. better than 8) |
| `research.max-revisions` | 2 | Extra writer/critic rounds after the first draft (up to **3** total iterations; LangChain4j uses `maxRevisions + 1`) |
| `research.default-depth` | 3 | Depth when the request omits it |
| `research.sse-timeout-ms` | 600000 | SSE emitter timeout |
| `research.temperature` | 0.2 | LangChain4j OpenAI client temperature |
| `research.max-tokens` | 4096 | Max completion tokens (`OPENROUTER_MAX_TOKENS`); applied on both engines |

## Shared prompts

`shared-prompts/*.system.txt` is copied into both apps as `classpath:prompts/` at Gradle `processResources` time. Keep those files identical so engine comparisons stay meaningful.

The Docker image build must include `shared-prompts/` in the build context (see `docker/Dockerfile`); otherwise startup fails with `Missing prompt resource: prompts/...`.

## Local ports

Avoids collisions with `java-mcp` (8080, 9080+) and `agents-2-agents` (8080, 8081).

| Port | Service |
|---:|---|
| 8090 | UI |
| 8091 | Spring AI |
| 8092 | LangChain4j |
