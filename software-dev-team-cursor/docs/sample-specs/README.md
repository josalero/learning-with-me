# Sample specs

These artifacts are the **shape** a successful run should produce for the demo feature:

> Return 404 with an RFC 9457 problem detail when a user id does not exist, and reject blank names on create.

They are fixtures for humans and for tests. Live runs write the same filenames under `runs/<runId>/`.

| File | Role | Java type |
| --- | --- | --- |
| [01-feature-brief.json](01-feature-brief.json) | Product Owner | `FeatureBrief` |
| [02-ai-spec.json](02-ai-spec.json) | Tech Lead | `AiSpec` |
| [03-change-summary.json](03-change-summary.json) | Developer | `ChangeSummary` |
| [04-build-result.json](04-build-result.json) | Build gate | `BuildResult` |
| [05-review-verdict.json](05-review-verdict.json) | PR Reviewer | `ReviewVerdict` |
| [06-qa-verdict.json](06-qa-verdict.json) | QA | `QaVerdict` |
| [08-stakeholder-decision.json](08-stakeholder-decision.json) | Stakeholder | `StakeholderDecision` |

`07-pull-request.diff` is git output, not JSON. A trimmed example is in [07-pull-request.diff](07-pull-request.diff).

## Traceability rule

`AiSpec.covers(brief)` is true only when every `acceptanceCriteria[].id` appears in `traceability[].acceptanceCriterionId` with a non-blank `plannedTest`. QA then scores each id individually.

## How to use these in a test

```java
FeatureBrief brief = mapper.readValue(fixture("01-feature-brief.json"), FeatureBrief.class);
AiSpec spec = mapper.readValue(fixture("02-ai-spec.json"), AiSpec.class);
assertThat(spec.covers(brief)).isTrue();
```
