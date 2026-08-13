# Architecture

The SDLC app is an orchestrator. It does not contain the users CRUD domain. It loads a **team** YAML and a **project** YAML, copies a **seed** into a gitignored workspace, and runs six roles (or a subset) as a LangChain4j agentic graph. The Developer writes real files. A deterministic build gate runs the project's allowlisted test command.

## System context

```mermaid
flowchart LR
  Human[Operator] -->|POST /api/v1/runs| Api[sdlc-app]
  Human -->|optional approve| Api
  Api --> Catalog[Team and project YAML]
  Api --> Workspace[workspace git repo]
  Api --> Runs[runs artifacts]
  Api --> OpenRouter[OpenRouter chat models]
  Workspace --> Build[Allowlisted build and test]
```

## Modules

`sdlc-core` has **no** LangChain4j or Spring dependency. `sdlc-app` wires agents, adapters, and HTTP.

```mermaid
flowchart TB
  subgraph app [sdlc-app]
    ApiLayer[api]
    Orch[orchestration]
    Agents[agent interfaces and tools]
    Adapters[adapter]
    Cfg[config]
  end
  subgraph core [sdlc-core]
    Domain[domain records]
    Ports[ports]
    Catalog[catalog records]
  end
  ApiLayer --> Orch
  Orch --> Agents
  Orch --> Adapters
  Orch --> Cfg
  Agents --> Domain
  Adapters --> Ports
  Cfg --> Catalog
  Ports --> Domain
  Catalog --> Domain
```

| Path | Responsibility |
| --- | --- |
| `sdlc-core/.../domain` | `FeatureBrief`, `AiSpec`, verdicts, `RunOutcome` |
| `sdlc-core/.../catalog` | `TeamBlueprint`, `ProjectProfile`, `TeamPolicy` |
| `sdlc-core/.../port` | `WorkspacePort`, `CommandRunner`, `VersionControlPort`, `ArtifactStore` |
| `sdlc-app/.../agent` | `@Agent` interfaces plus Developer/Tech Lead tools |
| `sdlc-app/.../orchestration` | `SdlcOrchestrator`, `SdlcPipelineFactory`, run service |
| `sdlc-app/.../adapter` | Path-jailed files, argv runner, local git, artifact store |
| `sdlc-app/.../config` | YAML catalogs, prompts, model factory, seeder |
| `sdlc-app/.../api` | REST + SSE + dashboard |

## Team vs technology

Java code does not mention Gradle, npm, or users-CRUD. Those live in project YAML. Who is on the team lives in team YAML.

```mermaid
flowchart TB
  TeamYaml[config/teams/*.yaml] --> TeamBlueprint
  ProjectYaml[config/projects/*.yaml] --> ProjectProfile
  TeamBlueprint --> Pipeline[Sdlc pipeline]
  ProjectProfile --> Pipeline
  Seed[seeds/stack-id] --> Workspace[workspace/stack-id]
  ProjectProfile --> Seed
  Pipeline --> Workspace
```

Two independent axes:

| Axis | File | Change when |
| --- | --- | --- |
| Team | `config/teams/<id>.yaml` | Different roles, models, loop caps |
| Technology | `config/projects/<id>.yaml` + `seeds/<id>/` | Different language, build, repo |

## Pipeline and loops

```mermaid
flowchart TD
  Start([Feature request]) --> PO[ProductOwner FeatureBrief]
  PO --> SpecLoop
  subgraph SpecLoop [L1 spec loop]
    TL[TechLead AiSpec] --> Covered{All ACs traced?}
    Covered -->|no and attempts left| TL
  end
  SpecLoop --> ImplLoop
  subgraph ImplLoop [L2 implementation loop]
    Dev[Developer file tools] --> Gate[Build gate real test cmd]
    Gate --> Green{Build green?}
    Green -->|no and attempts left| Dev
  end
  ImplLoop --> RevLoop
  subgraph RevLoop [L3 review loop]
    PR[PRReviewer git diff] --> NeedFix{REQUEST_CHANGES?}
    NeedFix -->|yes| Rework[Developer plus rebuild]
    Rework --> PR
  end
  RevLoop --> QaLoop
  subgraph QaLoop [L4 QA loop]
    QA[QA vs acceptance criteria] --> QaFail{FAIL?}
    QaFail -->|yes| QaFix[Developer plus rebuild]
    QaFix --> QA
  end
  QaLoop --> Commit[Commit branch write diff artifact]
  Commit --> SH[Stakeholder]
  SH -->|REJECTED and cycles left| PO
  SH -->|APPROVED| Done([COMPLETED])
  SH -->|cap reached| Escalated([ESCALATED])
```

LangChain4j builders:

| Stage | Builder | Exit |
| --- | --- | --- |
| Outer cycle | `loopBuilder` | stakeholder `APPROVED` or `maxStakeholderCycles` |
| L1 spec | `loopBuilder` | `AiSpec.covers(brief)` |
| L2 impl | `loopBuilder` | `BuildResult.success` |
| L3 review | `loopBuilder` + `conditionalBuilder` | `ReviewVerdict.approved` |
| L4 QA | `loopBuilder` + `conditionalBuilder` | `QaVerdict.passed(threshold)` |
| Build / commit / diff | `agentAction` | not an LLM |

Missing roles are skipped (lean pair can omit QA and Stakeholder). See [loops.md](loops.md). Time-ordered views (dashboard, create team/project, happy path, rework, human approval) are in [sequences.md](sequences.md).

## Sequence: happy path

Operator starts a run; artifacts land under `runs/<id>/`; git never pushes.

```mermaid
sequenceDiagram
    actor Operator
    participant API as sdlc-app
    participant PO as Product Owner
    participant TL as Tech Lead
    participant Dev as Developer
    participant Gate as Build gate
    participant PR as PR Reviewer
    participant QA as QA
    participant SH as Stakeholder

    Operator->>API: POST /api/v1/runs
    API->>PO: featureRequest
    PO-->>API: FeatureBrief
    API->>TL: brief + file tree
    TL-->>API: AiSpec (ACs traced)
    API->>Dev: spec + tools
    Dev-->>API: ChangeSummary
    API->>Gate: allowlisted test argv
    Gate-->>API: BuildResult success
    API->>PR: spec + git diff
    PR-->>API: APPROVE
    API->>QA: ACs + diff + test output
    QA-->>API: PASS
    API->>SH: brief + spec + QA
    SH-->>API: APPROVED
    API-->>Operator: COMPLETED + artifacts
```

## How agents see the repo

Never dump the whole tree into a prompt.

```mermaid
flowchart LR
  Tree[Filtered file tree plus conventions] --> Prompt[Always in the prompt]
  Tools[readFile listFiles] --> OnDemand[On demand]
  Diff[git diff] --> Stage[Reviewer and QA only]
```

## Safety

```mermaid
flowchart TB
  DevTools[Developer tools] --> Jail[Path jail vs repo root]
  DevTools --> Allow[Allowlisted argv only]
  Allow --> Isolated[Isolated GRADLE_USER_HOME and no-daemon]
  Git[LocalGit] --> NoPush[commit and diff never push]
```

- Paths resolve with `toRealPath()`; escapes and outbound symlinks throw `PathJailException`.
- Commands are argv arrays from project YAML. The agent cannot invent a shell string.
- Nested Gradle gets `--no-daemon` and `workspace/.gradle-home`.
- Secrets stay in env (`OPENROUTER_API_KEY`).

## Run artifacts

Each run writes `runs/<runId>/`:

| File | Producer |
| --- | --- |
| `01-feature-brief.json` | Product Owner |
| `02-ai-spec.json` | Tech Lead |
| `03-change-summary.json` | Developer |
| `04-build-result.json` | Build gate |
| `05-review-verdict.json` | PR Reviewer |
| `06-qa-verdict.json` | QA |
| `07-pull-request.diff` | Local git |
| `08-stakeholder-decision.json` | Stakeholder or human gate |
| `run.json` | Timeline + status |

Worked examples: [sample-specs/](sample-specs/).

## HTTP

| Method | Path | When |
| --- | --- | --- |
| `POST` | `/api/v1/runs` | Start a feature |
| `GET` | `/api/v1/runs/{id}` | Poll status and artifacts |
| `GET` | `/api/v1/runs/{id}/events` | SSE step stream |
| `POST` | `/api/v1/runs/{id}/approve` | Human stakeholder |
| `GET` | `/api/v1/teams` | Loaded blueprints |
| `POST` | `/api/v1/teams` | Create team YAML from the dashboard |
| `PUT` | `/api/v1/teams/{id}` | Update team YAML |
| `GET` | `/api/v1/projects` | Loaded profiles |
| `POST` | `/api/v1/projects` | Create project YAML from the dashboard |
| `PUT` | `/api/v1/projects/{id}` | Update project YAML |
| `POST` | `/api/v1/workspace/seed` | Copy seed into `workspace/` |

Call-level sequences for these endpoints: [sequences.md](sequences.md).

## Public types

See [roles.md](roles.md) for the record contracts. Javadoc on every public type in `sdlc-core` and `sdlc-app` includes a sample. `./gradlew check` enforces `MissingJavadocType` for public classes.
