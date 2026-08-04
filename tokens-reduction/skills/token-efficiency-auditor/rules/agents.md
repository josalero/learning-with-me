# Agent rules

Agent loops multiply token waste across reasoning steps.

## Detect

- **Repeated tool calls** — same tool + args in one session
- **Re-reading the same file** — coding agents reloading unchanged sources
- **Full tool results every step** — large outputs reattached to each model call
- **Passing all previous reasoning** — chain-of-thought history never compacted
- **Duplicated multi-agent context** — each agent gets the full shared pack
- **Endless planning loops** — no iteration / token budget
- **Repeated failed actions** — retries without changing strategy
- **Oversized handoffs** — dumping entire state between agents
- **Missing agent attribution** — usage cannot be split by workflow, agent, parent, or step
- **Per-agent-only budgets** — every child stays under its limit while the workflow total runs away
- **Recursive delegation** — agents keep spawning children without a depth or fan-out guard
- **Unscoped shared memory** — unrelated child agents read or write one global transcript

## Suggest

- Deduplicate tool calls; cache file reads per task
- Compress or summarize tool outputs before next step
- Enforce input-token budgets per agent cycle
- Observe / suggest / enforce middleware modes before auto-rewriting behavior
- Add loop guards and failed-action cooldowns
- Prefer compact context bundles (architecture + symbols) over whole repos
- Carry stable workflow, agent, parent, and step identifiers through every model and tool call
- Enforce both per-agent and workflow-wide budgets; include retries and handoffs
- Set delegation depth / fan-out limits and reject cycles
- Give each child task-scoped memory plus an explicit, size-limited shared context

## Evidence to collect

- Agent builder / orchestrator call sites
- Max steps, budgets, memory policy
- Tools shared vs selected per agent
- Agent topology: workflow ID, agent ID, parent ID, delegation depth, and fan-out
- Per-agent and aggregate workflow token usage, including retries
- Handoff payload size and overlap with parent / sibling context
- Shared-memory ownership, isolation, and concurrent update policy

## Multi-agent audit procedure

1. Draw the root-to-child topology and identify all handoff boundaries.
2. Attribute prompt, tool-schema, retrieval, memory, handoff, and output tokens to each agent.
3. Calculate the workflow total; do not add shared context more than once when reporting unique content.
4. Check per-request, per-agent, and per-workflow limits separately.
5. Flag missing identities, unbounded delegation, context copied to every child, and global mutable memory.
6. Require regression tests for task success, handoff completeness, budget behavior, and concurrent isolation.
