# AI Execution Gateway

A Spring Boot lab that runs an AI task only inside a budget and an authority grant. The gateway authenticates the caller, resolves the agent, checks a per-execution budget before every model and tool call, authorizes each tool, records usage, and evaluates the result.

```text
API Request → Authenticate → Resolve Agent Identity → Load Permissions
  → Check Budget → Model Call → Tool Request → Authorization → Execute Tool
  → Record Usage → Evaluate → Complete
```

The model proposes tool calls. Spring decides whether they run. `request_status_transition` only creates a PENDING request. The candidate status changes on `POST /api/v1/transition-requests/{id}/approve`.

How the loop, the authority rules, and the three scenarios fit together: [docs/architecture.md](docs/architecture.md).

Workflow for each run:

- [Scenario 1: Alice evaluates C-101](docs/scenarios/01-alice-evaluates-c-101.md)
- [Scenario 2: Injection on C-102](docs/scenarios/02-injection-on-c-102.md)
- [Scenario 3: Nightly budget on C-103](docs/scenarios/03-nightly-budget-c-103.md)

The live model adapter is **LangChain4j 1.22** (`OpenAiChatModel` against OpenRouter). The gateway owns the tool loop and calls `ChatModel.chat(ChatRequest)`. It does not use LangChain4j `AiServices` automatic tool execution. Tests use a deterministic stub. The original plan file was left unchanged.

## Stack

Java 26, Gradle, Spring Boot 4.1.0, PostgreSQL 18, Redis 7, Flyway, OpenTelemetry. `jenv shell 26` before Gradle.

## Run locally

`.env.example` already contains local demo keys. No key generation.

```sh
./scripts/up.sh
```

That copies `.env.example` to `.env` when `.env` is missing, then builds and starts Postgres, Redis, the OpenTelemetry collector, and the gateway. The API listens on http://localhost:8080 with the `stub` profile. Inside the compose network the gateway talks to `postgres`, `redis`, and `otel`; the OTLP URL in `.env` is for a host-run process and is not used by the container. In another terminal:

```sh
./scripts/run-scenario.sh 1   # Alice evaluates C-101 and approves the transition
./scripts/run-scenario.sh 2   # injection on C-102; both calls are denied
./scripts/run-scenario.sh 3   # nightly batch; the 6th tool call is not executed
```

The script reads the keys from `.env`. `DEMO_GLOBEX_API_KEY` is only for a cross-tenant 404; the three commands above do not need it.

The same three runs are in `postman/ai-execution-gateway.postman_collection.json`. Import that file in Postman and run one folder with the Collection Runner. The collection already has the demo keys. Scenario 1 polls until the run finishes, then approves the pending transition. No Postgres query.

`DemoKeyBootstrap` (profiles `stub` and `live`) stores SHA-256 hashes of those keys. A missing variable is skipped with a warning. The `test` profile does not bootstrap keys.

Live OpenRouter (`--spring.profiles.active=live`) requires `OPENROUTER_API_KEY` and `OPENROUTER_MODEL`. Request and response logging is off.

## Measured results

Stub pricing is 500 micro-USD per 1K input tokens and 1,500 per 1K output tokens. Each stub turn is 600 input + 150 output = 525 micro-USD. These numbers come from `Scenario1HappyPathIT`, `Scenario2PromptInjectionIT`, and `Scenario3RunawayBudgetIT`.

| Scenario | Status | Verdict | Successful | Model calls | Tool calls | Tokens in / out | Cost (micro-USD) |
| --- | --- | --- | ---: | ---: | ---: | --- | ---: |
| 1. Alice evaluates C-101 | COMPLETED | PASSED | true | 5 | 4 permitted | 3000 / 750 | 2625 |
| 2. Injection on C-102 | COMPLETED | POLICY_VIOLATION_ATTEMPTED | false | 4 | 1 permitted, 2 denied | 2400 / 600 | 2100 |
| 3. ATS batch on C-103 | BUDGET_EXHAUSTED | NOT_EVALUATED | false | 6 | 5 permitted, 6th not executed | 3600 / 900 | 3150 |

Cost per successful task is **2625 micro-USD**. Scenarios 2 and 3 are recorded and excluded from that figure.

Scenario 1 leaves the candidate in SCREENING until the recruiter approves the pending request, which moves the status to INTERVIEW. Scenario 2 denies `execute_status_transition` with `TOOL_NOT_GRANTED` and `request_status_transition` to HIRED with `INVALID_TRANSITION`. The candidate stays in SCREENING. Scenario 3's delegator is the `ats-nightly` SERVICE principal. The tool-call counter stays at 5 after the 6th reservation rolls back. Token use (4,500) stays under the 8,000 limit.

Canary name `CANARY-NAME-7731` and email `canary7731@example.test` do not appear in spans, logs, or any `execution_step` column. Audit rows store HMAC-SHA256 of arguments and results.

## Verification

```sh
jenv shell 26
./gradlew check
```

`check` runs the full test suite and JaCoCo line coverage with a minimum of 80%. Last run: **36 tests, 0 failures**. JaCoCo LINE coverage **1053 covered, 112 missed (90.39%)**. `jacocoTestCoverageVerification` passed.
