# Documentation

Guides for the resume seniority lab. The runnable summary stays in the [project README](../README.md).

| Guide | Read it when |
|---|---|
| [architecture.md](architecture.md) | You need the flow from HTTP to Jev and back |
| [api.md](api.md) | You are calling `POST /api/v1/seniority` or `GET /api/v1/samples` |
| [jev-decisions.md](jev-decisions.md) | You are changing the OpenRouter Decisions request or the parser |
| [rubric.md](rubric.md) | You are changing junior / intermediate / senior rules |
| [configuration.md](configuration.md) | You are changing ports, models, the API key, or the review threshold |
| [samples.md](samples.md) | You are adding a fixture resume or checking the startup demo |

## What this lab is

A Spring Boot 4.1 app on Java 26. It loads resume text and returns one of `JUNIOR`, `INTERMEDIATE`, or `SENIOR`.

Two models, two jobs:

| Piece | Model | Job |
|---|---|---|
| Decision | Jev `typesafe/jev-1.13` via `POST /api/alpha/decisions` | Label, confidence, probabilities |
| Explanation | Chat model via LangChain4j and OpenRouter `/api/v1` | Two Spanish sentences after the label is fixed |

Without `OPENROUTER_API_KEY`, both steps stay on the machine: a year-count rubric (`offline-years`) and a template sentence. `./gradlew check` never calls OpenRouter.

## Layout

```text
jenv-langchaing4j/
├── src/main/java/dev/mytechprofile/jev/
│   ├── JevSeniorityApplication.java   entry point
│   ├── DemoRunner.java                 classifies the classpath samples at startup
│   ├── api/                            HTTP, errors
│   ├── config/                         properties and WebClient / decider beans
│   ├── decide/                         Jev client, offline rubric, assessment
│   └── explain/                        LangChain4j explainer and template fallback
├── src/main/resources/resumes/         junior, intermediate, senior fixtures
└── src/test/java/                      no network; MockWebServer stands in for OpenRouter
```

## Upstream references

- [Jev on OpenRouter](https://openrouter.ai/docs/guides/community/jev)
- [Jev tutorial](https://openrouter.ai/docs/guides/community/jev-tutorial)
- [Decisions API reference](https://openrouter.ai/docs/api/api-reference/alphadecisions/submit-a-decisions-request)
