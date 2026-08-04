# Token Efficiency Audit Report

| Field | Value |
| --- | --- |
| Project | |
| Date | |
| Auditor | |
| Frameworks | Spring AI / LangChain4j / other |
| Scope | |
| Data basis | Static heuristics / runtime telemetry |

## Summary

| Metric | Value |
| --- | --- |
| Findings (total) | |
| High severity | |
| Medium severity | |
| Low / info | |
| Estimated avoidable input tokens (heuristic) | |
| Top waste category | prompts / tools / RAG / memory / agents |
| Workflows observed | |
| Agents observed | |
| Handoffs observed | |

## Findings

| ID | Severity | Area | Location | Issue | Est. tokens | Recommendation | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F1 | | | | | | | |

## Estimated consumption (heuristic)

Describe the heaviest paths and how estimates were produced (`count_tokens.py`, manual, runtime).

| Workflow | Agent | Parent | Path / operation | System | Tools | RAG | Memory | Handoff | User | Output | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | | | | |

## Multi-agent topology and controls

| Workflow | Agent | Parent | Depth | Steps / retries | Input tokens | Shared-context tokens | Handoff tokens | Agent budget | Workflow budget |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | | | |

Describe fan-out limits, cycle prevention, memory isolation, concurrent update policy, and how workflow totals are calculated.

## Recommended changes

1.
2.
3.

## Regression tests required

| Change | Assertion | Dataset / fixture |
| --- | --- | --- |
| | schemaValid / toolSelectionMatches / scoreDifferenceBelow | |
| Multi-agent budget | agentAndWorkflowLimitsApplied | root + parallel child fixture |
| Handoff compaction | handoffCompleteAndTaskSucceeds | parent/child delegation fixture |
| Shared memory | siblingStateIsIsolated | concurrent sibling fixture |

## Out of scope / unknowns

-

## Next engine steps

- [ ] Re-run with `token-audit scan` when core analyzers are implemented
- [ ] Capture runtime traces via Spring Boot starter
- [ ] Validate with evaluation / replay harness
