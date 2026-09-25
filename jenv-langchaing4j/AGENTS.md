# Agent notes — jev resume seniority

Java **26** (`jenv shell 26`), Spring Boot **4.1.0**, LangChain4j **1.18.1**. Gradle Groovy. Project directory name is `jenv-langchaing4j`; the Gradle project name is `jev-resume-seniority`.

## Read first

| Doc | Use |
|---|---|
| [README.md](README.md) | Run the lab |
| [docs/architecture.md](docs/architecture.md) | Decider vs explainer |
| [docs/jev-decisions.md](docs/jev-decisions.md) | Decisions request shape |
| [docs/api.md](docs/api.md) | HTTP contract |
| [docs/rubric.md](docs/rubric.md) | Offline years vs Jev criteria |
| [docs/configuration.md](docs/configuration.md) | Properties |

## Invariants

- Jev chooses the level. The chat model only explains it.
- `questions` on the Decisions API is a map keyed by name, not an array.
- `./gradlew check` must not call OpenRouter. Blank the API key in tests.
- Do not log the API key or the resume body.
- `502` bodies stay generic. Do not attach the upstream payload. Connection failures, timeouts, and invalid JSON are `502`, not `500`.
- Only `InvalidResumeException` maps to HTTP 400, plus an unreadable JSON body. Do not map `IllegalArgumentException`.
- Resume file reads stay inside the working directory after symlink resolution, and reject files over 64KB. Pasted text uses the same 64KB cap.

```bash
jenv shell 26
./gradlew check
./gradlew bootRun
```
