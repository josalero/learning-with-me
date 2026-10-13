# OpenRouter

LangChain4j 1.22 can use OpenRouter for both halves of the `app-1042` screen: the decision, and the posting extraction. They are different HTTP APIs.

## Why there are two clients

`OpenAiDecisionModel` builds an OpenAI client whose default origin is `https://api.openai.com/v1`. A decisions call is `POST {baseUrl}/decisions`. The JSON body has an `input` field and a `questions` array. Each question carries `type`, `name`, `instructions`, and either `choices` or `levels`.

OpenRouter's Decisions API is `POST https://openrouter.ai/api/alpha/decisions`. The body has `state` and a `questions` object keyed by the name you choose. A yes/no question is `noul`. An ordered rubric is `score`. A labeled set is `choice`. The chat catalog id `openai/gpt-6-luna` does not serve that endpoint. The decisions catalog id is `openai/gpt-6-luna-decisions`.

Pointing `OpenAiDecisionModel.baseUrl` at `https://openrouter.ai/api/v1` would post the OpenAI array to `/v1/decisions`, which is not this API. `OpenRouterDecisionModel` writes the OpenRouter body and reads the OpenRouter answers back into `YesNoAnswer` and `ScaleAnswer`.

Chat is the other way around. `OpenAiChatModel` posts `chat/completions` under `baseUrl`. OpenRouter serves that at `https://openrouter.ai/api/v1`. `OpenRouterPostingSkills.chatModel` sets that base URL. The sibling lab `jenv-langchaing4j` uses the same chat base URL for explanations.

## Screen request

`ApplicationScreen.screen(portfolio, recruiterNote)` becomes one decisions call. Question names are the record component names `advance` and `fit`. Because the portfolio is an image, `state` is an array. OpenRouter reads image parts only as top-level items of that array. A remote `http` URL is not fetched.

```json
{
  "model": "openai/gpt-6-luna-decisions",
  "state": [
    "recruiterNote: Partial match: Java and Spring Boot are present. Kubernetes is missing.",
    {
      "type": "image_url",
      "image_url": {
        "url": "data:image/png;base64,<AtsSample.PORTFOLIO_PNG>"
      }
    }
  ],
  "questions": {
    "advance": {
      "type": "noul",
      "instructions": "Should a recruiter review this application?"
    },
    "fit": {
      "type": "score",
      "instructions": "How well does it match the must-haves?",
      "criteria": [
        "Misses a must-have and should not advance",
        "Meets some must-haves and misses one",
        "Meets the must-haves named in the posting"
      ]
    }
  }
}
```

| LangChain4j | OpenRouter field |
| --- | --- |
| `YesNoQuestion.text()` | `questions.<name>.instructions` |
| `YesNoQuestion` | `type: noul` |
| `yesWhen` and `noWhen`, when both are set | `criteria.true` and `criteria.false` |
| `ScaleQuestion` | `type: score` |
| `@Description` on `FitLevel`, lowest first | `criteria` array |
| `ChoiceQuestion` | `type: choice`, criteria object of option to description |
| `ImageContent` base64 and mime type | `state[]` item `image_url.url` = `data:<mime>;base64,<bytes>` |
| Other named inputs, when an image is present | `state[]` strings `"name: value"` |
| Named inputs with no image | `state` object |

Accepted image types on this API are `image/png`, `image/jpeg`, and `image/webp`. The lab PNG is `image/png`.

## Screen response

```json
{
  "model": "openai/gpt-6-luna-decisions",
  "answers": {
    "advance": { "type": "noul", "noul": 0.86 },
    "fit": {
      "type": "score",
      "score": 1.0,
      "legend": {
        "0": "Misses a must-have and should not advance",
        "1": "Meets some must-haves and misses one",
        "2": "Meets the must-haves named in the posting"
      },
      "probabilities": { "0": 0.10, "1": 0.80, "2": 0.10 },
      "confidence": 0.80
    }
  },
  "usage": { "input_tokens": 120, "output_tokens": 40, "cost": 0.00002 }
}
```

| OpenRouter field | LangChain4j |
| --- | --- |
| `answers.advance.noul` | `YesNoAnswer` probability. `DecisionServices` treats 0.5 and above as yes |
| `answers.fit.score` | `ScaleAnswer.mean()`, a 0-based index. `1.0` is `FitLevel.PARTIAL` |
| `answers.fit.probabilities."0"` ... `"2"` | `ScaleAnswer.probabilities()`, in enum order |
| `answers.fit.confidence` | `ScaleAnswer.confidence()` when the field is a number |
| `answers.fit.legend` | Checked by you in the JSON. `ScaleAnswer` has no legend field |
| `usage.input_tokens`, `usage.output_tokens` | `DecisionResponse.tokenUsage()` |
| `usage.cost` | Left in the JSON. This lab does not store it |

A missing probability index, a wrong `type`, or `answers` as an array throws `OpenRouterDecisionException`. The message names the question. It does not include the note, the image, or the raw body.

## HTTP behavior

| Setting | Value |
| --- | --- |
| Authorization | `Bearer` plus `OPENROUTER_API_KEY` |
| Connect timeout | 5 seconds |
| Response timeout | 30 seconds (`OpenRouterDecisionModel.RESPONSE_TIMEOUT`) |
| Redirects | Never followed, so the bearer token stays on the host you set |
| Logging | Off. slf4j-nop is on the runtime classpath for the rest of the lab |

| Failure | Exception message |
| --- | --- |
| Blank key at construction | `OpenRouter API key is blank. Set OPENROUTER_API_KEY.` |
| HTTP status outside 2xx | `OpenRouter decisions HTTP <status>` |
| Empty body | `OpenRouter decisions returned an empty body` |
| Timeout | `OpenRouter decisions timed out` |
| Connection failure | `OpenRouter decisions unreachable` |
| Image with a URL and no bytes | `OpenRouter decisions does not fetch remote image URLs. Pass base64 image bytes.` |

`OpenRouterDecisionModel` owns HTTP only. `OpenRouterRequestMapper.toJson` writes the body and `OpenRouterResponseMapper.fromJson` reads the answers. Both mappers are package-private, so their tests sit in the same package and run without a network call. `OpenRouterDecisionModelReadsLocalDecisionsResponseTest` posts to `127.0.0.1` and checks the `Authorization` header, the `PARTIAL` level, and that a 401 message does not contain the response body. A second constructor accepts your own `HttpClient`. Keep redirects off on it so the bearer token never follows a redirect to another host.

## Chat request

`PostingSkills.extract` is an AI Service. 1.22 puts `AtsSample.POSTING` in the user message once. OpenRouter receives that same message at the chat endpoint.

```java
ChatModel chat = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .baseUrl("https://openrouter.ai/api/v1")
        .modelName("openai/gpt-4o-mini")
        .temperature(0.0)
        .logRequests(false)
        .logResponses(false)
        .build();
PostingSkills skills = AiServices.create(PostingSkills.class, chat);
String line = skills.extract(TextContent.from(AtsSample.POSTING));
```

`OpenRouterPostingSkills.chatModel` is that builder. `whenKeyPresent(ProviderEnvironment.system())` reads `OPENROUTER_API_KEY` and `OPENROUTER_CHAT_MODEL`. Building it does not send the posting.

## What stays offline

`./gradlew run` always screens with `NoteBasedScreeningModel`. A partial note (the words `partial` or `missing`) is `FitLevel.PARTIAL`, and advance is yes. A strong note is `FitLevel.STRONG`. Anything else is `NO_MATCH` and advance is no. That model also checks that an `ImageContent` arrived, which is the 1.22 behavior under test.

Set a key and call `OpenRouterApplicationScreen.whenKeyPresent(ProviderEnvironment.system())` yourself when you want GPT-6 Luna Decisions to answer `app-1042`.
