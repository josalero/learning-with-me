# LangChain4j 1.22.0

One applicant-tracking story for application `app-1042`, a backend role that requires Java, Spring Boot, and Kubernetes. The three samples in `./gradlew run` need no API key.

The portfolio image is a one-pixel PNG standing in for a diagram of a Spring service. It is a work sample. It is not a photo of the candidate, and nothing in this lab scores a face.

| Step | What the recruiter does | What 1.22 changed |
| --- | --- | --- |
| Screen | A portfolio screenshot and the note "Kubernetes is missing" become a yes/no and a fit scale | A decision service can take an `ImageContent`. `@Description` on a scale level is the text the model sees |
| Read the file | `readApplication` opens `app-1042.txt` from the applications folder. The recruiter cancels | `executeToolAsync(...).cancel(true)` sends `notifications/cancelled` with reason `Cancelled`. `workingDirectory` is that folder |
| Read the posting | The must-have sentence is a `@UserMessage TextContent` | 1.22 sends that text once. 1.21 appended it a second time |

Class-by-class notes are in [docs/samples.md](docs/samples.md).

## OpenRouter

Yes. Two different OpenRouter APIs are wired, and neither is called by `./gradlew test` or `./gradlew run`.

| Job | OpenRouter URL | LangChain4j type | Model |
| --- | --- | --- | --- |
| Screen `app-1042` | `POST https://openrouter.ai/api/alpha/decisions` | `OpenRouterDecisionModel` | `openai/gpt-6-luna-decisions` |
| Extract must-haves from the posting | `POST https://openrouter.ai/api/v1/chat/completions` | `OpenAiChatModel.baseUrl(...)` | `openai/gpt-4o-mini` |

`OpenAiDecisionModel.baseUrl("https://openrouter.ai/api/v1")` posts OpenAI's questions array to `/v1/decisions`. OpenRouter's decisions body is a `questions` object plus `state`, at `/api/alpha/decisions`. The adapter in `OpenRouterDecisionModel` writes that body. The chat client can use `baseUrl` because chat completions are OpenAI-compatible.

```java
ProviderEnvironment env = ProviderEnvironment.system();

Optional<ApplicationScreen> screen = OpenRouterApplicationScreen.whenKeyPresent(env);
screen.ifPresent(live -> live.screen(
        ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"),
        AtsSample.RECRUITER_NOTE));

Optional<PostingSkills> skills = OpenRouterPostingSkills.whenKeyPresent(env);
skills.ifPresent(live -> live.extract(TextContent.from(AtsSample.POSTING)));
```

| Variable | Required | Default |
| --- | --- | --- |
| `OPENROUTER_API_KEY` | for either live call | empty, and the offline models run |
| `OPENROUTER_DECISION_MODEL` | no | `openai/gpt-6-luna-decisions` |
| `OPENROUTER_CHAT_MODEL` | no | `openai/gpt-4o-mini` |

A live screen sends the recruiter note and the portfolio bytes to OpenRouter. Keep the key out of the repo and out of logs. The adapter does not log the request or the response. A 401 comes back as `OpenRouter decisions HTTP 401` with no body attached.

Full request and response shapes: [docs/openrouter.md](docs/openrouter.md).

`OpenAiApplicationScreen` still builds OpenAI's own `gpt-6-luna` client when `OPENAI_API_KEY` is set. That path is a different account and a different JSON contract. Bedrock batch and Anthropic `cacheAutomatically` need their own accounts, so they are not called here.

## Run

```bash
cd langchain4j-1.22
jenv shell 26
./gradlew test
./gradlew run
```

`run` prints `advance=true fit=PARTIAL`, `posting copies=1`, and `reason=Cancelled` for `app-1042`. It also prints how to turn on the OpenRouter screen. The demo keeps using the offline note model even when a key is present, so a local run does not spend a request.

The file read pins protocol `2025-11-25` so stdio sends the cancellation notification. The server may still ignore it. Cancelling the future does not stop a tool the AI Service has already started.

## What the tests check

| Test | Proves |
| --- | --- |
| `ApplicationScreenMapsPortfolioAndFitDescriptionsTest` | The offline screen advances a partial note and keeps the `@Description` text |
| `OpenAiDecisionModelUsesGpt6LunaTest` | Building `OpenAiDecisionModel` names `gpt-6-luna` and does not call the API |
| `OpenRouterRequestMapperWritesStateAndQuestionObjectTest` | The portfolio becomes a `data:image/png;base64` item, `fit` is a `score` question, and a remote image URL is rejected |
| `OpenRouterResponseMapperReadsAnswersTest` | `noul` and `score` map back, a missing probability fails, and a wrong `type` names the question without echoing the body |
| `OpenRouterDecisionModelReadsLocalDecisionsResponseTest` | A local HTTP fixture returns `PARTIAL`, a 401 does not leak the body, and a blank key is rejected |
| `ProviderEnvironmentTreatsBlankAsMissingTest` | A blank variable falls back to the default, and every live client stays offline without a key |
| `PostingTextIsSentOnceTest` | The posting string appears once in the user message, and the counter sees two copies when there are two |
| `OpenRouterPostingSkillsPointsChatAtOpenRouterTest` | The chat client is named `openai/gpt-4o-mini` and is not called |
| `CancelledStdioToolSendsNotificationTest` | Cancel writes `requestId=<n> reason=Cancelled` from the applications folder |
| `CancellingOneReadLetsOtherReadsFinishTest` | Cancelling request 7 does not suppress the result for request 8 |

## Design

Packages follow the ATS job each class does, not the LangChain4j feature it shows:

| Package | ATS job | 1.22 feature it shows |
| --- | --- | --- |
| `screening` | Decide whether `app-1042` advances and how well it fits | Decision services, `Scale`, `@Description`, image input |
| `screening.openrouter` | Run that screen on OpenRouter's Decisions API | Custom `DecisionModel` |
| `posting` | Read the must-have skills from a job posting | `TextContent` sent once |
| `applicationfiles` | Read an application file through an MCP tool, then cancel it | MCP `notifications/cancelled` |
| `provider` | Read API keys and model overrides | none, shared by the live clients |
| `sample` | The `app-1042` strings every package uses | none |

`screening`, `posting`, and `applicationfiles` do not import each other. Only `WhatsNew` ties them together.

Component, package, and sequence diagrams for each scenario: [docs/architecture.md](docs/architecture.md).

The OpenRouter adapter is split by reason to change. `OpenRouterDecisionModel` owns HTTP only. `OpenRouterRequestMapper` writes the body and `OpenRouterResponseMapper` reads the answers. Both are package-private in `screening.openrouter`, so each mapping is tested without a socket and the public surface stays one class. The `HttpClient` can be passed in for a proxy or custom TLS.

Question types are mapped with a pattern-matching `switch`. LangChain4j's `Question` is not sealed, so the `default` branch rejects a type this lab does not know, instead of guessing.

Live clients read keys through `ProviderEnvironment`, so tests pass a map instead of touching `System.getenv`. `ApplicationScreen` is the port the demo depends on. The offline, OpenAI, and OpenRouter models are interchangeable behind it.
