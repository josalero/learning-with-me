# Samples

Every class in this lab is about application `app-1042`.

```text
Application app-1042
Role: backend
Must-haves: Java, Spring Boot, Kubernetes
Recruiter note: Java and Spring Boot are present. Kubernetes is missing.
Portfolio: a one-pixel PNG, standing in for a service diagram
```

`AtsSample` holds those strings so the decision, the file read, and the posting extraction share one story.

## Screen

`FitLevel` is ordered from lowest to highest: `NO_MATCH`, `PARTIAL`, `STRONG`. Each constant has `@Description`. LangChain4j 1.22 sends that description as the scale level text and keeps the constant name as the label.

`ScreenResult` is the return type:

```java
public record ScreenResult(
        @Decide("Should a recruiter review this application?") boolean advance,
        @Decide("How well does it match the must-haves?") Scale<FitLevel> fit) {}
```

`@Decide` belongs on the record components. A `@Decide` on the method is rejected when the return type is a record. Parameter names need `javac -parameters`, which `build.gradle` turns on. The input arrives as a map whose keys are `portfolio` and `recruiterNote`.

`ApplicationScreen.screen` takes `ImageContent` first. 1.22 passes that image through. A photo of the candidate is out of scope. The sample image is `ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png")`.

`NoteBasedScreeningModel` is the offline stand-in:

| Note contains | Fit level | Advance probability |
| --- | --- | --- |
| `partial` or `missing` | 1, `PARTIAL` | 0.86, yes |
| `strong` or `meets every`, and neither word above | 2, `STRONG` | 0.86, yes |
| anything else | 0, `NO_MATCH` | 0.08, no |

`partial` is checked before `strong`, so the sample note stays `PARTIAL` even though it also says which skills are present. The model stores `lastScaleDescriptions()` from the scale question. The test expects `Meets some must-haves and misses one`. Probabilities live in an `EnumMap<FitLevel, List<Double>>`, so the scale mean is `fit.ordinal()` rather than a separate index. A question that is neither yes/no nor scale is rejected.

`DecisionServices.create(ApplicationScreen.class, model)` builds the proxy. The same interface is used with three models:

| Class | When |
| --- | --- |
| `NoteBasedScreeningModel` | `./gradlew test` and `./gradlew run` |
| `OpenRouterDecisionModel` | `OPENROUTER_API_KEY` is set and you call `OpenRouterApplicationScreen` |
| `OpenAiDecisionModel` via `OpenAiApplicationScreen` | `OPENAI_API_KEY` is set. The builder names `gpt-6-luna`. This lab never calls `decide` on it |

Boolean threshold in decision services is 0.5. Scale `mostLikely()` is the level nearest the mean. Mean `1.0` with the mass on index 1 is `PARTIAL`.

## OpenRouter adapter

The adapter lives in `screening.openrouter`:

| Class | Responsibility |
| --- | --- |
| `OpenRouterDecisionModel` | Implements `DecisionModel`. Posts the body and maps HTTP failures |
| `OpenRouterRequestMapper` | Writes `state` and the `questions` object. Package-private |
| `OpenRouterResponseMapper` | Reads `noul`, `score`, and `choice` back into LangChain4j answers. Package-private |
| `OpenRouterApplicationScreen` | Builds `ApplicationScreen` when `OPENROUTER_API_KEY` is set |

Details and the JSON samples are in [openrouter.md](openrouter.md).

`OpenRouterDecisionException` is the failure type. Messages name the status or the missing field and leave out the note, the image, and the key.

## Posting

`PostingSkills.extract` is an AI Service method:

```java
String extract(@UserMessage TextContent posting);
```

`RecordingChatModel.doChat` stores the `ChatRequest` and returns `Java, Spring Boot, Kubernetes`. `UserMessageText.copiesOf` counts how many times `AtsSample.POSTING` appears inside user `TextContent`. `WhatsNew` and `PostingTextIsSentOnceTest` both use it. The expected count is 1.

`OpenRouterPostingSkills` is the live version of that call. It uses `OpenAiChatModel` with base URL `https://openrouter.ai/api/v1` and model `openai/gpt-4o-mini`, unless `OPENROUTER_CHAT_MODEL` is set. Temperature is 0. Request and response logging are off.

## File read

`CancelledToolCall.cancelApplicationRead` writes `app-1042.txt` into a temp directory, starts `ApplicationFileServer` with stdio, and sets `workingDirectory` to that directory. On macOS `/var` is a symlink to `/private/var`, so the test compares `toRealPath()` on both sides.

The server speaks newline-delimited JSON-RPC. Stdout is JSON only. Diagnostics go to stderr. After `initialize` the client sends `notifications/initialized` and does not wait. The lab server answers `initialize` with protocol `2025-11-25`. The client is pinned to the same version so it sends `notifications/cancelled`. The modern protocol `2026-07-28` does not send that notification on this path.

`readApplication` takes `applicationId`. The server writes `started.txt` as `applicationId=app-1042 requestId=<id>`, then waits on a virtual thread. Each read has its own latch keyed by request id, so a cancel for one id never suppresses another read. `CancellingOneReadLetsOtherReadsFinishTest` drives `ApplicationFileServer.handle` directly, without a child process, to prove that. The test waits for that file, then `cancel(true)`. The future completes with `CancellationException`. The server writes `cancelled.txt` as `requestId=<id> reason=Cancelled`. The id is assigned by the client. The test allows any digits. In a local run it has been `3`, after initialize and `tools/list`.

The JVM flag `--enable-final-field-mutation=ALL-UNNAMED` is on the test task, the `run` task, and the child server process. Jackson 2, which `langchain4j-mcp` 1.22 uses, writes the final `jsonrpc` field. Java 26 warns on that write unless the flag is set.

## What a cancelled future does not do

`cancel(true)` cancels the client future and, on this protocol, sends the notification. A tool that an AI Service has already started is a separate call. `@CompensateFor` does not cover MCP tools. This lab shows the notification the server can see. It does not claim the server stopped work it had already finished.

## Entry point

`WhatsNew.main` prints four lines:

```text
screen app-1042: advance=true fit=PARTIAL partialDescriptionSeen=true
posting copies=1
read applicationId=app-1042 requestId=<id>
cancel requestId=<id> reason=Cancelled
```

A fifth line says whether `OPENROUTER_API_KEY` is visible. The key does not change the four lines above.
