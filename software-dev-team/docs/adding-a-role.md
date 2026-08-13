# Adding a role kind

Composing existing roles is YAML-only ([adding-a-team.md](adding-a-team.md)). A **new** `RoleKind` is a Java change. Touch every row:

| Step | Where |
| --- | --- |
| 1. Add the enum constant | `sdlc-core/.../domain/RoleKind.java` |
| 2. Add the agent interface | `sdlc-app/.../agent/<Name>Agent.java` with `@V(StateKeys…)` and `outputKey` |
| 3. Add a prompt file | `prompts/<name>.md` |
| 4. Decide who writes which state | [agent-state.md](agent-state.md) — add a row |
| 5. Wire the pipeline | `SdlcPipelineFactory` + `AgentFactory` |
| 6. Persist an artifact if the role produces one | `ArtifactFile` + `RunOutcomeAssembler` |
| 7. Restart | `GET /api/v1/role-kinds` lists the new name; the Teams tab loads kinds from that API |

Do not hardcode the kind list in `static/app.js`.

## Sample

Adding `SECURITY_REVIEWER` means: enum value, `SecurityReviewerAgent`, `prompts/security-reviewer.md`, a `securityVerdict` state key, a loop (or a single step) in `SdlcPipelineFactory`, and optionally `09-security-verdict.json`.
