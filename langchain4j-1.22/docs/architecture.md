# Architecture

Every diagram follows application `app-1042`: a backend role whose must-haves are Java, Spring Boot, and Kubernetes. The recruiter note says Kubernetes is missing. The portfolio is a one-pixel PNG standing in for a service diagram. It is a work sample, never a photo of the candidate.

The diagrams are Mermaid, so GitHub and most Markdown previews render them. PNG copies for slides, wikis, or viewers without Mermaid are in [diagrams/](diagrams/). See [PNG exports](#png-exports).

## Components

Three ATS jobs, each in its own package. `WhatsNew` is the only class that uses all three. Dashed arrows only happen with an API key, so `./gradlew test` and `./gradlew run` never leave the machine.

```mermaid
flowchart LR
    Main["WhatsNew"]

    subgraph lab["This lab"]
        direction TB
        Screening["screening<br/>screen app-1042"]
        Posting["posting<br/>read must-have skills"]
        Files["applicationfiles<br/>read, then cancel"]
        Shared["provider + sample"]
    end

    subgraph lc4j["LangChain4j 1.22"]
        direction TB
        Decisions["DecisionServices"]
        Ai["AiServices"]
        Mcp["DefaultMcpClient"]
    end

    subgraph outside["Outside the JVM"]
        direction TB
        OrDecisions[("OpenRouter decisions")]
        OrChat[("OpenRouter chat")]
        Child[["ApplicationFileServer<br/>child JVM"]]
    end

    Main --> Screening
    Main --> Posting
    Main --> Files
    Screening --> Decisions
    Posting --> Ai
    Files --> Mcp
    Screening --> Shared
    Posting --> Shared
    Files --> Shared
    Decisions -.-> OrDecisions
    Ai -.-> OrChat
    Mcp -- "stdio JSON-RPC" --> Child
```

### Inside screening

`ApplicationScreen` is the interface the caller depends on. `DecisionServices` turns it into a proxy over any `DecisionModel`, so the three models can be swapped without touching the caller.

```mermaid
flowchart LR
    Caller["WhatsNew or a test"]
    Screen["ApplicationScreen<br/>returns ScreenResult"]
    Proxy["DecisionServices proxy"]

    subgraph models["DecisionModel implementations"]
        direction TB
        Offline["NoteBasedScreeningModel<br/>offline"]
        OpenAi["OpenAiDecisionModel<br/>built by OpenAiApplicationScreen"]
        OrModel["OpenRouterDecisionModel<br/>built by OpenRouterApplicationScreen"]
    end

    subgraph adapter["screening.openrouter internals"]
        direction TB
        Req["OpenRouterRequestMapper"]
        Res["OpenRouterResponseMapper"]
    end

    OrApi[("POST /api/alpha/decisions")]
    OpenAiApi[("OpenAI /v1/decisions<br/>not called by the lab")]

    Caller --> Screen
    Screen --> Proxy
    Proxy --> Offline
    Proxy --> OpenAi
    Proxy --> OrModel
    OrModel --> Req
    OrModel --> Res
    OrModel -.-> OrApi
    OpenAi -.-> OpenAiApi
```

### Inside posting

```mermaid
flowchart LR
    Caller["WhatsNew or a test"]
    Skills["PostingSkills<br/>extract(TextContent)"]
    Proxy["AiServices proxy"]
    Recording["RecordingChatModel<br/>offline"]
    Chat["OpenAiChatModel<br/>built by OpenRouterPostingSkills"]
    Counter["UserMessageText<br/>copiesOf"]
    OrChat[("https://openrouter.ai/api/v1<br/>chat/completions")]

    Caller --> Skills --> Proxy
    Proxy --> Recording
    Proxy --> Chat
    Chat -.-> OrChat
    Caller --> Counter
    Counter -. reads lastRequest .-> Recording
```

### Inside applicationfiles

```mermaid
flowchart LR
    Flow["CancelledToolCall"]
    Client["DefaultMcpClient<br/>protocol 2025-11-25"]
    Transport["StdioMcpTransport<br/>workingDirectory = folder"]
    Server["ApplicationFileServer<br/>child JVM"]
    Folder[("applications folder<br/>app-1042.txt, started.txt,<br/>cancelled.txt, cwd.txt")]

    Flow --> Client --> Transport
    Transport -- "stdin / stdout" --> Server
    Flow --> Folder
    Server --> Folder
```

## Package dependencies

```mermaid
flowchart TB
    root["dev.mytechprofile.langchain122<br/>WhatsNew"]
    screening["screening"]
    openrouter["screening.openrouter"]
    posting["posting"]
    files["applicationfiles"]
    provider["provider"]
    sample["sample"]

    root --> screening
    root --> openrouter
    root --> posting
    root --> files
    root --> provider
    root --> sample
    openrouter --> screening
    openrouter --> provider
    screening --> provider
    posting --> provider
    posting --> sample
    files --> sample
```

`screening`, `posting`, and `applicationfiles` never import each other. Tests import `sample` freely.

## Screen offline

`./gradlew run` and `ApplicationScreenMapsPortfolioAndFitDescriptionsTest` follow this path. No HTTP call is made.

```mermaid
sequenceDiagram
    autonumber
    participant Main as WhatsNew
    participant Proxy as ApplicationScreen proxy
    participant Model as NoteBasedScreeningModel

    Main->>Proxy: screen(ImageContent portfolio, recruiterNote)
    Note over Proxy: DecisionServices builds a DecisionRequest<br/>input map: portfolio, recruiterNote<br/>advance = YesNoQuestion<br/>fit = ScaleQuestion with the FitLevel descriptions
    Proxy->>Model: decide(request)
    Model->>Model: require an ImageContent in the input
    Model->>Model: note contains "missing", so FitLevel.PARTIAL
    Model->>Model: remember the scale descriptions
    Model-->>Proxy: advance = yes 0.86, fit mean 1.0 with probabilities 0.10 / 0.80 / 0.10
    Note over Proxy: 0.86 is above the 0.5 threshold<br/>mostLikely() of mean 1.0 is PARTIAL
    Proxy-->>Main: ScreenResult(advance=true, fit=PARTIAL)
```

## Screen on OpenRouter

Used when `OPENROUTER_API_KEY` is set and you call `OpenRouterApplicationScreen.whenKeyPresent`. The tests run the same path against a local `HttpServer` on `127.0.0.1`.

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant Factory as OpenRouterApplicationScreen
    participant Env as ProviderEnvironment
    participant Proxy as ApplicationScreen proxy
    participant Model as OpenRouterDecisionModel
    participant Req as OpenRouterRequestMapper
    participant Http as HttpClient
    participant OR as OpenRouter decisions
    participant Res as OpenRouterResponseMapper

    Caller->>Factory: whenKeyPresent(env)
    Factory->>Env: value OPENROUTER_API_KEY
    Env-->>Factory: key, or empty and the caller stays offline
    Factory->>Env: valueOr OPENROUTER_DECISION_MODEL, gpt-6-luna-decisions
    Factory-->>Caller: Optional of ApplicationScreen

    Caller->>Proxy: screen(portfolio, recruiterNote)
    Proxy->>Model: decide(DecisionRequest)
    Model->>Req: toJson(request, modelName)
    Note over Req: state becomes an array because of the image<br/>"recruiterNote: ..." plus an image_url data URL<br/>questions object: advance noul, fit score
    Req-->>Model: JSON body
    Model->>Http: POST, Authorization Bearer key, 30 s timeout
    Http->>OR: POST /api/alpha/decisions
    OR-->>Http: 200 with answers and usage
    Http-->>Model: body
    Model->>Res: fromJson(request, body)
    Note over Res: check each answer type against the question sent<br/>noul 0.86 becomes YesNoAnswer<br/>score 1.0 plus probabilities "0".."2" becomes ScaleAnswer<br/>usage becomes TokenUsage 120 / 40
    Res-->>Model: DecisionResponse
    Model-->>Proxy: DecisionResponse
    Proxy-->>Caller: ScreenResult(advance=true, fit=PARTIAL)

    alt status outside 2xx
        OR-->>Model: 401 with any body
        Model-->>Caller: OpenRouterDecisionException "OpenRouter decisions HTTP 401"
    else timeout or no connection
        Model-->>Caller: OpenRouterDecisionException "timed out" or "unreachable"
    else wrong type or missing probability
        Res-->>Caller: OpenRouterDecisionException naming the question, never the body
    end
```

The exception text never includes the key, the note, the image, or the response body.

## Read the posting skills

`PostingTextIsSentOnceTest` and `./gradlew run` use `RecordingChatModel`. With a key, `OpenRouterPostingSkills` swaps in `OpenAiChatModel` pointed at `https://openrouter.ai/api/v1`.

```mermaid
sequenceDiagram
    autonumber
    participant Main as WhatsNew
    participant Proxy as PostingSkills proxy
    participant Chat as ChatModel
    participant Count as UserMessageText

    Main->>Proxy: extract(TextContent POSTING)
    Note over Proxy: AiServices builds one UserMessage<br/>1.22 attaches the TextContent once
    Proxy->>Chat: chat(ChatRequest)
    alt offline
        Chat->>Chat: RecordingChatModel stores the request
    else OPENROUTER_API_KEY set
        Chat->>Chat: OpenAiChatModel posts chat/completions to OpenRouter
    end
    Chat-->>Proxy: AiMessage "Java, Spring Boot, Kubernetes"
    Proxy-->>Main: "Java, Spring Boot, Kubernetes"
    Main->>Count: copiesOf(lastRequest, POSTING)
    Count-->>Main: 1
```

## Cancel an application file read

`CancelledStdioToolSendsNotificationTest` and `./gradlew run` follow this path. The child server is a second JVM started by `StdioMcpTransport`.

```mermaid
sequenceDiagram
    autonumber
    participant Flow as CancelledToolCall
    participant Client as DefaultMcpClient
    participant Server as ApplicationFileServer (child JVM)
    participant Folder as applications folder
    participant Worker as virtual thread

    Flow->>Folder: write app-1042.txt
    Flow->>Client: build with StdioMcpTransport, workingDirectory = folder
    Client->>Server: start java ApplicationFileServer
    Server->>Folder: write cwd.txt
    Client->>Server: initialize, protocol 2025-11-25
    Server-->>Client: protocolVersion 2025-11-25
    Client->>Server: notifications/initialized
    Flow->>Client: listTools()
    Client->>Server: tools/list
    Server-->>Client: readApplication(applicationId)

    Flow->>Client: executeToolAsync(readApplication, app-1042)
    Client->>Server: tools/call id=3
    Server->>Server: register a cancel latch for id 3
    Server->>Folder: write started.txt "applicationId=app-1042 requestId=3"
    Server->>Worker: wait up to 4 s on the latch
    Flow->>Folder: poll until started.txt exists

    Flow->>Client: future.cancel(true)
    Client->>Server: notifications/cancelled requestId=3 reason=Cancelled
    Server->>Worker: count down latch 3
    Server->>Folder: write cancelled.txt "requestId=3 reason=Cancelled"
    Worker-->>Server: cancelled, sends no result
    Client-->>Flow: future throws CancellationException
    Flow->>Folder: read cancelled.txt
    Flow->>Client: close() the client and its stdio transport
    Flow-->>Flow: Outcome(folder, startedNote, cancellationNote)
```

The client assigns the request id. It has been `3` in local runs, after `initialize` and `tools/list`, and the test accepts any number. The notification tells the server the client stopped waiting. It does not prove the server stopped work. A tool an AI Service has already started is a separate call, and `@CompensateFor` does not cover MCP tools.

## Cancel one read, finish another

`CancellingOneReadLetsOtherReadsFinishTest` drives `ApplicationFileServer.handle` directly, without a child process. Each read has its own latch, so cancelling request 7 cannot suppress request 8.

```mermaid
sequenceDiagram
    autonumber
    participant Test
    participant Server as ApplicationFileServer
    participant W7 as worker for id 7
    participant W8 as worker for id 8
    participant Out as output lines

    Test->>Server: handle tools/call id=7
    Server->>W7: wait 300 ms on latch 7
    Test->>Server: handle tools/call id=8
    Server->>W8: wait 300 ms on latch 8
    Test->>Server: handle notifications/cancelled requestId=7
    Server->>W7: count down latch 7
    W7-->>Server: cancelled, no result
    Note over W8: latch 8 untouched
    W8->>Out: result id=8 with the app-1042 file body
    Test->>Out: expect id 8 present, id 7 absent
```

## Where to change what

| Change | Start in |
| --- | --- |
| A new screening question | `ScreenResult` record components with `@Decide` |
| How a question type maps to OpenRouter | `OpenRouterRequestMapper.question` and `OpenRouterResponseMapper.answer` |
| HTTP timeouts, headers, or failure messages | `OpenRouterDecisionModel` |
| A new environment variable | The factory that needs it, read through `ProviderEnvironment` |
| MCP tool schema or cancellation behavior | `ApplicationFileServer` |
| The `app-1042` story text | `AtsSample` |

## PNG exports

Each Mermaid block above has a PNG copy, in the same order:

| Section | PNG |
| --- | --- |
| Components | [components-overview.png](diagrams/components-overview.png) |
| Inside screening | [components-screening.png](diagrams/components-screening.png) |
| Inside posting | [components-posting.png](diagrams/components-posting.png) |
| Inside applicationfiles | [components-applicationfiles.png](diagrams/components-applicationfiles.png) |
| Package dependencies | [package-dependencies.png](diagrams/package-dependencies.png) |
| Screen offline | [sequence-screen-offline.png](diagrams/sequence-screen-offline.png) |
| Screen on OpenRouter | [sequence-screen-openrouter.png](diagrams/sequence-screen-openrouter.png) |
| Read the posting skills | [sequence-read-posting-skills.png](diagrams/sequence-read-posting-skills.png) |
| Cancel an application file read | [sequence-cancel-application-read.png](diagrams/sequence-cancel-application-read.png) |
| Cancel one read, finish another | [sequence-cancel-one-read-finish-another.png](diagrams/sequence-cancel-one-read-finish-another.png) |

The Mermaid source in this file is the source of truth. After editing a diagram, regenerate the PNGs from the lab root. This needs Node; `npx` downloads `@mermaid-js/mermaid-cli` on first use.

```bash
work=$(mktemp -d)
awk -v dir="$work" '/^```mermaid/{f=1;n++;out=sprintf("%s/d%02d.mmd",dir,n);next} /^```/{f=0} f{print > out}' docs/architecture.md
names=(components-overview components-screening components-posting components-applicationfiles package-dependencies sequence-screen-offline sequence-screen-openrouter sequence-read-posting-skills sequence-cancel-application-read sequence-cancel-one-read-finish-another)
i=1
for name in "${names[@]}"; do
  npx -y -p @mermaid-js/mermaid-cli mmdc -i "$(printf '%s/d%02d.mmd' "$work" $i)" -o "docs/diagrams/$name.png" -b white -s 2 -q
  i=$((i+1))
done
```

Adding or reordering a Mermaid block shifts the numbering, so update `names` to match.
