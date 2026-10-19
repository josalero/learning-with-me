# Scenario 3: Nightly budget on C-103

`ats-nightly` is a service account that evaluates C-103 and never stops searching. The grant allows five tool calls. The sixth reservation fails, the tool is not executed, and the run ends `BUDGET_EXHAUSTED`. The service cannot approve anything, because approval is not in its scopes.

Run it with `./scripts/run-scenario.sh 3` or the Postman folder **3 Nightly budget**. The caller is `ats-nightly` (`DEMO_ATS_API_KEY`).

## Starting point

| Fact | Value |
| --- | --- |
| Tenant | acme |
| Principal | `ats-nightly`, kind `SERVICE` |
| Scopes | `candidates:read`, `evidence:read` |
| Candidate | C-103, status `SCREENING`, requisition R-7 |
| Evidence | E-4, E-5 |
| Grant | 20 model calls, **5 tool calls**, 8,000 tokens, 50,000 micro-USD |

The token cap is 8,000 so the tool-call cap trips first. Six model turns would be 4,500 tokens, which is still under 8,000.

## Workflow

```mermaid
sequenceDiagram
  participant ATS as ats-nightly
  participant Loop as Orchestrator
  participant Budget as BudgetGuard
  participant Model as Stub
  ATS->>Loop: POST /executions C-103
  loop turns 1 to 5
    Loop->>Budget: reserve model call, then 1 tool call
    Budget-->>Loop: allowed
    Model-->>Loop: search_evidence
    Loop->>Loop: PERMITTED
  end
  Loop->>Budget: turn 6, reserve model call
  Budget-->>Loop: allowed, 4500 tokens
  Loop->>Budget: reserve 6th tool call
  Budget-->>Loop: over limit, roll back
  Loop-->>ATS: BUDGET_EXHAUSTED NOT_EVALUATED
```

1. The service sends `POST /api/v1/executions` for C-103. The grant matches this principal and `screening-agent`. The API returns `202`. The delegator recorded on the execution is `ats-nightly`, kind `SERVICE`.
2. **Turns 1 through 5.** The stub calls `search_evidence` every turn, with query `q1` through `q5`. Each turn reserves one model call (750 tokens, 525 micro-USD) and then one tool call. Authorization permits the search (`evidence:read`). Five tool steps are stored.
3. **Turn 6.** The model-call reservation still fits: 6 model calls and 4,500 tokens are inside the grant. The stub again asks for `search_evidence`. The tool-call reservation would make the counter 6, past the limit of 5. `BudgetGuard` decrements that reservation back to 5 and throws `BudgetExhaustedException`. The sixth tool is never authorized and never executed. There is no sixth tool step.
4. The orchestrator sets status `BUDGET_EXHAUSTED`. The evaluator short-circuits on that status: verdict `NOT_EVALUATED`, `successful=false`. It does not look for a draft.
5. C-103 stays in `SCREENING`. There is no transition request to approve. `ats-nightly` also lacks `transition:approve`, so an approve call from this key would be `403`.

Redis key `budget:{executionId}:toolCalls` stays at 5 after the rollback.

## What you should see

| Check | Expected |
| --- | --- |
| Execution status | `BUDGET_EXHAUSTED` |
| Verdict | `NOT_EVALUATED`, `successful=false` |
| Delegator | `ats-nightly`, kind `SERVICE` |
| Model calls | 6 |
| Tool calls requested | 5 |
| Sixth tool call | not executed |
| Tokens | 3,600 in / 900 out (4,500 total, under 8,000) |
| Cost | 3,150 micro-USD |
| Candidate status | still `SCREENING` |

This cost is recorded and excluded from cost-per-successful-task. Only scenario 1 counts toward that figure.
