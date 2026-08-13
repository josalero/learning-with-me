# Sequence diagrams

Time-ordered views of the dashboard, catalog writes, and the SDLC pipeline. Flowcharts for the same loops live in [architecture.md](architecture.md) and [loops.md](loops.md).

Participants use the names the operator sees in the UI and `runs/<id>/run.json`.

## 1. Dashboard: list teams and projects

On load, the UI fills the Samples, Teams, and Projects tabs from the catalog.

```mermaid
sequenceDiagram
    actor Operator
    participant UI as Dashboard
    participant API as sdlc-app API
    participant Catalog as CatalogLoader
    participant Disk as config/ YAML

    Operator->>UI: Open http://localhost:8095/
    UI->>API: GET /api/v1/teams
    API->>Catalog: loadTeams()
    Catalog->>Disk: read config/teams/*.yaml
    Disk-->>Catalog: TeamBlueprint list
    Catalog-->>API: teams
    API-->>UI: 200 teams

    UI->>API: GET /api/v1/projects
    API->>Catalog: loadProjects()
    Catalog->>Disk: read config/projects/*.yaml
    Disk-->>Catalog: ProjectProfile list
    Catalog-->>API: projects
    API-->>UI: 200 projects

    UI->>API: GET /api/v1/prompts
    API-->>UI: prompt paths
    UI->>API: GET /api/v1/seeds
    API-->>UI: seed directory ids
    UI-->>Operator: Teams, Projects, Feature form ready
```

## 2. Create a team or a project

Creates persist YAML under `config/`. No Java change. The next `GET` (and the next run) sees the new id without restarting, because `CatalogLoader` re-reads disk.

```mermaid
sequenceDiagram
    actor Operator
    participant UI as Dashboard
    participant API as sdlc-app API
    participant Writer as CatalogWriter
    participant Loader as CatalogLoader
    participant Disk as config/

    Operator->>UI: Fill Teams form, Save
    UI->>API: POST /api/v1/teams
    API->>Writer: saveTeam(document, overwrite=false)
    alt id already exists
        Writer-->>API: CatalogConflictException
        API-->>UI: 409 Problem Detail
        UI-->>Operator: Team id already exists
    else new id
        Writer->>Disk: write config/teams/{id}.yaml
        Writer->>Loader: readTeam(tmp) validate
        Loader-->>Writer: TeamBlueprint
        Writer-->>API: team
        API-->>UI: 201 team
        UI-->>Operator: Team appears in the list
    end

    Operator->>UI: Fill Projects form, Save
    UI->>API: POST /api/v1/projects
    API->>Writer: saveProject(document, overwrite=false)
    Writer->>Loader: seed dir exists, repoPath under workspace/
    alt validation fails
        Loader-->>Writer: CatalogException
        API-->>UI: 400 Problem Detail
        UI-->>Operator: Fix seed path or argv commands
    else valid
        Writer->>Disk: write config/projects/{id}.yaml
        API-->>UI: 201 project
        UI-->>Operator: Project appears in the list
    end
```

Update uses `PUT /api/v1/teams/{id}` and `PUT /api/v1/projects/{id}` with `overwrite=true`.

## 3. Start a feature and watch results

The Samples tab starts a numbered recipe, posts a run, then the Results tab follows SSE plus a final `GET`.

```mermaid
sequenceDiagram
    actor Operator
    participant UI as Dashboard
    participant API as RunController
    participant Runs as RunService
    participant Orch as SdlcOrchestrator
    participant SSE as GET .../events

    Operator->>UI: Choose team + project, paste feature, Start
    UI->>API: POST /api/v1/runs
    API->>Runs: start(RunCommand)
    alt OPENROUTER_API_KEY missing
        Runs-->>API: CatalogException
        API-->>UI: 400 add a key
    else accepted
        Runs-->>API: RunOutcome PENDING
        API-->>UI: 202 {runId, status}
        UI->>SSE: GET /api/v1/runs/{id}/events
        Runs->>Orch: run(runId, command) on virtual thread
        loop each agent or gate
            Orch-->>Runs: StepEvent
            Runs-->>SSE: event: step
            SSE-->>UI: timeline row
        end
        Orch-->>Runs: COMPLETED or ESCALATED or FAILED
        UI->>API: GET /api/v1/runs/{id}
        API-->>UI: FeatureBrief, AiSpec, verdicts, artifacts
        UI-->>Operator: Results: status, timeline, JSON, diff
    end
```

Optional before Start: **Seed workspace** calls `POST /api/v1/workspace/seed` so `workspace/<projectId>` exists before the first Developer write.

## 4. Happy path (default-scrum-team)

One stakeholder cycle. Every AC is traced, tests pass, review and QA approve, stakeholder ships. Missing roles are omitted (see lean-pair in [adding-a-team.md](adding-a-team.md)).

```mermaid
sequenceDiagram
    actor Operator
    participant Orch as SdlcOrchestrator
    participant PO as Product Owner
    participant TL as Tech Lead
    participant Dev as Developer
    participant Gate as Build gate
    participant PR as PR Reviewer
    participant QA as QA
    participant Git as LocalGit
    participant SH as Stakeholder
    participant Artifacts as runs/{id}/

    Operator->>Orch: POST /api/v1/runs
    Orch->>Orch: seed workspace, create feature branch

    Orch->>PO: featureRequest
    PO-->>Orch: FeatureBrief
    Orch->>Artifacts: 01-feature-brief.json

    loop L1 until AiSpec.covers(brief) or maxSpecRework
        Orch->>TL: brief + fileTree + conventions
        TL-->>Orch: AiSpec
    end
    Orch->>Artifacts: 02-ai-spec.json

    loop L2 until BuildResult.success or maxImplementationAttempts
        Orch->>Dev: aiSpec + fileTree + buildFeedback
        Dev->>Dev: listFiles / readFile / writeFile
        Dev-->>Orch: ChangeSummary
        Orch->>Gate: allowlisted test argv (skipped if no file changes)
        Gate-->>Orch: BuildResult
    end
    Orch->>Artifacts: 03-change-summary.json
    Orch->>Artifacts: 04-build-result.json

    loop L3 until ReviewVerdict.approved or maxReviewCycles
        Orch->>Git: workingTreeDiff
        Git-->>Orch: gitDiff
        Orch->>PR: aiSpec + gitDiff
        PR-->>Orch: APPROVE
    end
    Orch->>Artifacts: 05-review-verdict.json

    loop L4 until QaVerdict.passed(threshold) or maxQaCycles
        Orch->>QA: brief + spec + gitDiff + buildOutput
        QA-->>Orch: PASS
    end
    Orch->>Artifacts: 06-qa-verdict.json

    Orch->>Git: capture diff, commitAll
    Git-->>Orch: unified diff
    Orch->>Artifacts: 07-pull-request.diff

    Orch->>SH: brief + spec + qa + changeSummary
    SH-->>Orch: APPROVED
    Orch->>Artifacts: 08-stakeholder-decision.json
    Orch->>Artifacts: run.json COMPLETED
    Orch-->>Operator: Results tab shows COMPLETED
```

## 5. Review or QA asks for changes

L3 and L4 wrap Developer + build gate in `conditionalBuilder`. The Developer runs only when the previous verdict is not a pass.

```mermaid
sequenceDiagram
    participant Orch as SdlcOrchestrator
    participant PR as PR Reviewer
    participant Dev as Developer
    participant Gate as Build gate
    participant QA as QA

    Orch->>PR: aiSpec + gitDiff
    PR-->>Orch: REQUEST_CHANGES + findings

    alt review requests changes and cycles remain
        Orch->>Dev: reviewFeedback
        Dev->>Dev: writeFile tests and production code
        Dev-->>Orch: ChangeSummary
        Orch->>Gate: test argv
        Gate-->>Orch: BuildResult
        Orch->>PR: refreshed gitDiff
        PR-->>Orch: APPROVE
    end

    Orch->>QA: spec + diff + buildOutput
    QA-->>Orch: FAIL missingTests

    alt QA failed and cycles remain
        Orch->>Dev: qaFeedback
        Dev-->>Orch: ChangeSummary
        Orch->>Gate: test argv
        Gate-->>Orch: BuildResult
        Orch->>QA: refreshed evidence
        QA-->>Orch: PASS score >= threshold
    end
```

## 6. Human stakeholder

When team YAML has `stakeholderMode: HUMAN`, the pipeline pauses at `WAITING_APPROVAL`. The Results tab shows Approve / Reject.

```mermaid
sequenceDiagram
    actor Operator
    participant UI as Dashboard
    participant API as sdlc-app API
    participant Orch as SdlcOrchestrator
    participant Gate as HumanApprovalGate
    participant Artifacts as runs/{id}/

    Note over Orch: QA passed, diff artifact written
    Orch->>Artifacts: run.json WAITING_APPROVAL
    Orch->>Gate: await(runId, timeout)
    Gate-->>Operator: Results: waiting for approval

    Operator->>UI: Approve or Reject + reasons
    UI->>API: POST /api/v1/runs/{id}/approve
    API->>Gate: complete(runId, StakeholderDecision)
    Gate-->>Orch: APPROVED or REJECTED
    Orch->>Artifacts: 08-stakeholder-decision.json

    alt APPROVED
        Orch->>Artifacts: run.json COMPLETED
    else REJECTED and stakeholder cycles remain
        Orch->>Orch: outer loop: PO again with followUps
    else cap reached
        Orch->>Artifacts: run.json ESCALATED
    end
```

Agent stakeholder mode skips this gate: the Stakeholder LLM writes `08-stakeholder-decision.json` directly.

## 7. Stakeholder rejects (outer loop)

`maxStakeholderCycles` bounds how many times the whole inner sequence may repeat.

```mermaid
sequenceDiagram
    participant Orch as SdlcOrchestrator
    participant PO as Product Owner
    participant Inner as L1 L2 L3 L4 + commit
    participant SH as Stakeholder

    Orch->>PO: featureRequest, followUps=""
    PO-->>Orch: FeatureBrief v1
    Orch->>Inner: implement v1
    Inner-->>Orch: green build, QA PASS
    Orch->>SH: decide
    SH-->>Orch: REJECTED + followUps

    alt cycles remaining
        Orch->>PO: featureRequest + stakeholderFollowUps
        PO-->>Orch: FeatureBrief v2
        Orch->>Inner: implement v2
        Inner-->>Orch: green build, QA PASS
        Orch->>SH: decide
        SH-->>Orch: APPROVED
        Note over Orch: COMPLETED
    else maxStakeholderCycles hit
        Note over Orch: ESCALATED, read run.json steps
    end
```

## 8. Developer tools and safety

The Developer never receives a shell string. Paths that leave the repo root fail. Git never pushes.

```mermaid
sequenceDiagram
    participant Dev as Developer agent
    participant Tools as DeveloperTools
    participant WS as FileWorkspace
    participant Gate as BuildGate
    participant Git as LocalGit

    Dev->>Tools: writeFile("../secret", "...")
    Tools->>WS: writeFile
    WS-->>Tools: PathJailException
    Tools-->>Dev: error (stay inside repo)

    Dev->>Tools: writeFile("src/UserController.java", source)
    Tools->>WS: resolveInsideJail + write
    WS-->>Dev: wrote file

    Note over Gate: Runs the allowlisted test command after the Developer turn, and skips when git is clean.

    Note over Git: commitAll and workingTreeDiff only. No push.
```

## 9. Loop cap without a passing verdict

Any `loopBuilder` that exhausts `maxIterations` without its exit condition leaves the run `ESCALATED`, not spinning.

```mermaid
sequenceDiagram
    participant Orch as SdlcOrchestrator
    participant Dev as Developer
    participant Gate as Build gate

    loop maxImplementationAttempts
        Orch->>Dev: implement
        Dev-->>Orch: ChangeSummary
        Orch->>Gate: test
        Gate-->>Orch: failure
    end
    Note over Orch: Build still red. Later stages may still run.<br/>Final status ESCALATED if stakeholder does not approve a green build.
    Orch-->>Orch: write run.json status=ESCALATED
```

## How to read these against code

| Diagram | Start here |
| --- | --- |
| 1–3 Dashboard and runs | `sdlc-app/.../api`, static `/` |
| 4–7 Pipeline and HITL | `SdlcOrchestrator`, [loops.md](loops.md) |
| 8 Safety | `FileWorkspace`, `ProcessCommandRunner`, `LocalGit` |
| Artifacts on disk | `runs/<runId>/`, [sample-specs/](sample-specs/) |
