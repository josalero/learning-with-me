# How to run the AI SDLC team

This guide is the operational path: install, verify offline, seed a repo, start a feature, read artifacts, then add a second team or stack.

## 1. Prerequisites

- JDK **26** via jenv (`jenv shell 26`)
- Docker is **not** required for `bootRun`; use `docker compose up --build` if you want the packaged stack
- `git` on `PATH`
- An [OpenRouter](https://openrouter.ai/keys) key only for live LLM runs (not for `./gradlew check` or `SDLC_OFFLINE=true`)

```bash
cd software-dev-team
jenv shell 26
java -version    # 26.x
```

## 2. Configure models

```bash
cp .env.example .env
```

| Variable | Role |
| --- | --- |
| `OPENROUTER_API_KEY` | Required for `bootRun`, `llmTest`, `scripts/demo.sh` |
| `SDLC_LLM_TIMEOUT` | Per OpenRouter HTTP call (default `PT3M`). Raise to `PT5M` if you see `TimeoutException` |
| `MODEL_FAST` | Product Owner, QA, Stakeholder (default `openai/gpt-4o-mini`) |
| `MODEL_STRONG` | Tech Lead, Developer, PR Reviewer |
| `SDLC_HOME` | Catalog/workspace/runs root (default `.`) |
| `SDLC_PORT` | HTTP port (default `8095`) |
| `SDLC_OFFLINE` | `true` uses a canned `ChatModel` (no API key). Does not implement the demo feature. |

Export the file into the shell, or rely on your process manager. Spring reads `OPENROUTER_*` and `MODEL_*` from the environment.

## 3. Offline quality gate

No API key. No network LLM calls.

```bash
jenv shell 26
./gradlew check
```

That compiles both modules, runs unit tests (excluding `@Tag("llm")`), Checkstyle (`MissingJavadocType` on public types), and Spotless.

Live-model tests:

```bash
export OPENROUTER_API_KEY=...
./gradlew llmTest
```

## 4. Seed a playground repo

Seeds are templates. The team edits a **copy** under gitignored `workspace/`.

```bash
./scripts/seed-workspace.sh users-service-java
# creates workspace/users-service-java, git init, initial commit
```

The Java seed is a small users CRUD with two deliberate gaps the demo feature fills:

- unknown user id does not return an RFC 9457 404
- create accepts a blank name

## 5. Start the orchestrator

```bash
jenv shell 26
export SDLC_OFFLINE=true
./gradlew :sdlc-app:bootRun
```

Dashboard: `http://localhost:8095/` (Feature, Teams, Projects, Results).

For live models, `export $(grep -v '^#' .env | xargs)` instead of `SDLC_OFFLINE`.

## 5b. Docker Compose

Same dashboard and API, with JDK 26, git, and Node inside the image so nested Gradle/`npm test` can run. Runtime is **JDK**, not JRE.

```bash
cp .env.example .env   # optional OPENROUTER_API_KEY
SDLC_OFFLINE=true docker compose up --build
```

Compose reads `.env` from this directory. Live runs need a non-empty `OPENROUTER_API_KEY` and `SDLC_OFFLINE=false`. Offline mode starts without a key.

| Host path | Container |
| --- | --- |
| `config/` | `/data/config` |
| `prompts/` | `/data/prompts` |
| `seeds/` | `/data/seeds` |
| `docs/` | `/data/docs` |
| `workspace/` | `/data/workspace` |
| `runs/` | `/data/runs` |

`SDLC_HOME` inside the container is `/data`. Port: `http://localhost:8095/` (`SDLC_PORT` on the host).

Image: [docker/Dockerfile](../docker/Dockerfile). Compose: [docker-compose.yml](../docker-compose.yml).

The image downloads Gradle 9.4.1 with `curl` retries. A `SocketException: Unexpected end of file` during `./gradlew` was a truncated wrapper download, not a compile error. Rebuild with `docker compose build --no-cache sdlc-app` if a half-downloaded zip is cached.

## 6. Start a feature run

Open `http://localhost:8095/`. The **Samples** tab is a numbered walkthrough, not a grid of similar cards.

Run **step 1** first (Start here). After Results, come back and run the next number. Each card has a different question and a **Look for this** checklist.

1. Smoke — solo developer, Java seed
2. Small team — lean pair on Java
3. Full team — six-role scrum on Java
4. Second stack — same feature, Node seed
5. Human in the loop — you are the stakeholder
6. Quality — vague request, inspect the Product Owner brief

**Reset & run** recopies the seed (so the 404 / blank-name gaps are back), starts the run, and opens Results. Custom team/project forms sit under **Custom run**.

Offline (`SDLC_OFFLINE=true`) walks the pipeline with canned JSON and **does not implement** the feature. Live OpenRouter is required to change seed code.

Custom runs still work: pick team/project, **Fill demo feature**, **Seed workspace** or **Reset workspace**, **Start run**. HTTP details: [api.md](api.md).

```bash
curl -sS -X POST http://localhost:8095/api/v1/runs \
  -H 'Content-Type: application/json' \
  -d '{
    "teamId": "default-scrum-team",
    "projectId": "users-service-java",
    "featureRequest": "Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create."
  }'
```

Poll:

```bash
curl -sS http://localhost:8095/api/v1/runs/<runId>
```

SSE timeline:

```bash
curl -N http://localhost:8095/api/v1/runs/<runId>/events
```

Human stakeholder (only when the team YAML has `stakeholderMode: HUMAN`):

```bash
curl -sS -X POST http://localhost:8095/api/v1/runs/<runId>/approve \
  -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVED","reasons":["Matches the brief"],"followUps":[]}'
```

## 7. Read the output

Artifacts land in `runs/<runId>/`. The demo feature's expected shapes are checked in against [sample-specs/](sample-specs/).

| File | Question it answers |
| --- | --- |
| `01-feature-brief.json` | What are we building? |
| `02-ai-spec.json` | Which files and tests? |
| `03-change-summary.json` | What did the Developer touch? |
| `04-build-result.json` | Did `./gradlew test` (or npm test) pass? |
| `05-review-verdict.json` | Did the diff match the spec? |
| `06-qa-verdict.json` | Did every AC have evidence? |
| `07-pull-request.diff` | The local "PR" |
| `08-stakeholder-decision.json` | Ship or send back? |
| `run.json` | Status, branch, step timings |

The working tree is `workspace/users-service-java` on a `feature/...` branch. Nothing is pushed.

## 8. One-shot demo

```bash
export SDLC_OFFLINE=true
# app already running at :8095
./scripts/demo.sh
```

Seeds Java, starts a run, waits, then repeats against `users-service-node` with the same feature text. That is the proof that technology is config.

## 9. Add a team (no Java change)

Copy [config/teams/default-scrum-team.yaml](../config/teams/default-scrum-team.yaml) to `config/teams/my-team.yaml`. Change `id`, drop roles you do not want, or pin models.

Lean pair (PO + Developer + Reviewer only) is already in [config/teams/lean-pair.yaml](../config/teams/lean-pair.yaml). Developer-only and human-stakeholder teams: [config/teams/dev-only.yaml](../config/teams/dev-only.yaml), [config/teams/hitl-lean.yaml](../config/teams/hitl-lean.yaml).

Restart `bootRun` so catalogs reload. Then pass `"teamId": "my-team"` in `POST /api/v1/runs`.

Details: [adding-a-team.md](adding-a-team.md).

## 10. Add a technology (no Java change)

1. Put a compilable seed under `seeds/<id>/`.
2. Add `config/projects/<id>.yaml` with argv arrays (`build`, `test`) — never a shell string.
3. Keep `repoPath` under `workspace/`.
4. Add `docs/conventions/<stack>.md` and point `conventions:` at it.
5. `./scripts/seed-workspace.sh <id>` and start a run with `"projectId": "<id>"`.

Node example: [config/projects/users-service-node.yaml](../config/projects/users-service-node.yaml).

## 11. Typical failures

| Symptom | What to check |
| --- | --- |
| App refuses to start | Team YAML unknown `kind`, missing prompt file, `repoPath` outside `workspace/` — the error is a numbered shopping list |
| Developer cannot compile | `MODEL_STRONG` too weak; raise it. Fallback is a tighter spec (per-file edits) |
| `./gradlew test` looks stuck on the first run | Expected once per machine: the wrapper downloads Gradle plus Spring dependencies into `workspace/.gradle-home` (~5-10 min on a cold cache). Later runs reuse it. Watch progress with `docker compose logs -f sdlc-app` |
| Nested Gradle hangs | Runner injects `--no-daemon` and `GRADLE_USER_HOME=workspace/.gradle-home`, and kills the process tree at `timeoutSeconds` (600s for `users-service-java`) |
| Agent reads `build/reports/...` | Fixed: `build`, `.gradle`, `.git`, `node_modules`, `target`, `out`, `dist`, `coverage` are hidden from `listFiles`/`readFile`. Rebuild the app if it still happens |
| Gradle `test` repeats with no file edits | Rebuild the app. Tests run only in the build gate, and skip when git is clean (`build-gate` status `skipped`) |
| `PathJailException` | Agent tried `../` or an absolute path — expected |
| `CommandNotAllowedException` | Agent or test asked for a command name not in project YAML |
| `AgentInvocationException: Failed to invoke agent method: UntypedAgent.invoke` | Wrapper. The Results error now names the inner role and root cause after rebuild. Review/QA are skipped when tests failed, which is what usually provoked this |
| Developer cannot compile `@WebMvcTest` | Spring Boot 4 needs `spring-boot-starter-webmvc-test` (now in the Java seed). Do not let the agent edit `build.gradle`. Reset the workspace after rebuilding |
| `OutputParsingException: Failed to parse null` | Reasoning models left `content` empty. Rebuild the app (thinking JSON is recovered). Or lower `MODEL_STRONG` reasoning, or raise `OPENROUTER_MAX_TOKENS` |
| `ResponseBodyEmitter has already completed` | A closed browser tab left a dead SSE stream. Harmless and now dropped silently; rebuild the app if the log still appears |
| Review/QA loop until the cap with a green build | Untracked files were missing from `git diff HEAD`; QA `score=0` despite PASS rows; empty stakeholder reject restarted the cycle. Rebuild the app |
| `WAITING_APPROVAL` | Human stakeholder; call `POST .../approve` |

## 12. Where to look in code

| Task | Start here |
| --- | --- |
| Domain contracts | `sdlc-core/.../domain` |
| YAML loading | `CatalogLoader` |
| File jail | `FileWorkspace` |
| Build gate | `ProcessCommandRunner` |
| Pipeline | `SdlcOrchestrator` / `SdlcPipelineFactory` |
| HTTP | `RunController`, [api.md](api.md) |
| State keys | [agent-state.md](agent-state.md) |

Architecture diagrams: [architecture.md](architecture.md). Sequence diagrams: [sequences.md](sequences.md).
