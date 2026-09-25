# Configuration

Properties live in `src/main/resources/application.yml`. Secrets stay in the environment. Copy `.env.example` to `.env`, set the key, and export it yourself. Do not commit `.env`.

```bash
cp .env.example .env
set -a && source .env && set +a
jenv shell 26
./gradlew bootRun
```

## JDK

`.java-version` is `26`. Gradle's toolchain is Java 26. Before `./gradlew`, run `jenv shell 26` and check `java -version`.

## Server

| Property | Default | Role |
|---|---|---|
| `server.port` | `8094`, override with `SERVER_PORT` | HTTP port |
| `spring.application.name` | `jev-resume-seniority` | Log tag |
| `spring.main.web-application-type` | `servlet` | Tomcat, even though WebClient comes from the WebFlux starter |

`bootRun` sets the working directory to the project root so `path` values such as `src/main/resources/resumes/junior.txt` resolve.

## App

| Property | Default | Role |
|---|---|---|
| `app.demo` | `true`, override with `APP_DEMO` | When `true`, `DemoRunner` classifies every classpath sample at startup |
| `app.human-review-below` | `0.70` | Confidence strictly below this sets `needsHumanReview`. Must be from `0.0` through `1.0` inclusive; otherwise startup fails |

Tests force `app.demo=false` and a blank `openrouter.api-key`.

## OpenRouter

| Property | Default | Role |
|---|---|---|
| `openrouter.api-key` | `OPENROUTER_API_KEY`, empty | Blank selects the offline decider and the template explainer |
| `openrouter.base-url` | `https://openrouter.ai/api` | Decisions API origin. No trailing path |
| `openrouter.decisions-path` | `/alpha/decisions` | Appended to the base URL |
| `openrouter.jev-model` | `typesafe/jev-1.13` | `model` field. Alias `~typesafe/jev-latest` tracks the newest Jev |
| `openrouter.chat-base-url` | `https://openrouter.ai/api/v1` | LangChain4j OpenAI-compatible base. This is not the Decisions URL |
| `openrouter.chat-model` | `openai/gpt-4o-mini` | Explanation model only |
| `openrouter.explain` | `true` | When `false`, a live key still calls Jev and the explanation stays on the template |

`liveCallsEnabled()` is true only when the key is non-blank. The key is sent as a bearer token and as the LangChain4j client credential. It is not written to logs. The key may be blank. `base-url`, `decisions-path`, `jev-model`, `chat-base-url`, and `chat-model` must be non-blank or startup fails.

## Modes

| Key | `explain` | Decision | Explanation |
|---|---|---|---|
| absent | either | `offline-years` | template |
| present | `true` | `jev` | chat model, template if that call fails |
| present | `false` | `jev` | template |

## Timeouts

Hard-coded on the Decisions `WebClient`, not in YAML:

| Limit | Value |
|---|---|
| TCP connect | 5 seconds |
| HTTP response | 20 seconds (`OpenRouterJevDecider.RESPONSE_TIMEOUT`, also the WebClient response timeout) |

The chat client uses LangChain4j defaults plus `maxTokens` 200.
