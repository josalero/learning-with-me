# Agentic state keys

Every role reads and writes a shared `AgenticScope`. Keys are Java constants in `StateKeys`. A typo does not fail the run — `RunStateFactory.read` logs a WARN and uses a fallback.

| Key | Type | Writer | Readers |
| --- | --- | --- | --- |
| `featureRequest` | `String` | `RunStateFactory.initialState` | Product Owner |
| `stakeholderFollowUps` | `String` | initial empty; human/agent reject | Product Owner |
| `featureBrief` | `FeatureBrief` | Product Owner (or synthetic if the role is absent) | Tech Lead, QA, Stakeholder, persist |
| `aiSpec` | `AiSpec` | Tech Lead (or synthetic) | Developer, Reviewer, QA, Stakeholder |
| `fileTree` | `String` | `RunStateFactory.refresh` | Tech Lead, Developer |
| `conventions` | `String` | refresh | Tech Lead, Developer, Reviewer |
| `changeSummary` | `ChangeSummary` | Developer | Stakeholder, persist |
| `buildResult` | `BuildResult` | deterministic build gate | implementation-loop exit; QA reconcile (executed test names) |
| `buildFeedback` | `String` | build gate (same text as output) | Developer |
| `buildOutput` | `String` | build gate, including `Executed tests:` from JUnit XML | QA |
| `reviewVerdict` | `ReviewVerdict` | PR Reviewer (empty content falls back to APPROVE JSON), then `ReviewEvidence.reconcile` | review-loop exit, persist |
| `reviewFeedback` | `String` | review rework `beforeCall` | Developer |
| `qaVerdict` | `QaVerdict` | QA agent, then `QaEvidence.reconcile` | qa-loop exit, Stakeholder |
| `qaFeedback` | `String` | QA rework `beforeCall` | Developer |
| `gitDiff` | `String` | refresh (source only; keeps last non-empty diff after commit) + commit action | Reviewer, QA |
| `stakeholderDecision` | `StakeholderDecision` | Stakeholder or HITL, then `StakeholderDecision.reconcile` | outer loop exit |
| `runId` / `branch` | `String` | orchestrator | artifacts / git |

Prompt placeholders (`{{featureBrief}}`) must match these names. Agent `@V` / `outputKey` must use `StateKeys`.

Numbered files: see [`ArtifactFile`](../sdlc-core/src/main/java/dev/mytechprofile/sdlc/domain/ArtifactFile.java) and [how-to.md](how-to.md) §7.
