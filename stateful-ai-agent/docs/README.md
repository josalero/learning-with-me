# Documentation

Read these in order. Each page assumes the one before it.

| Order | Page | Question it answers |
| --- | --- | --- |
| 1 | [hiring-process.md](hiring-process.md) | Which stages exist, who may ask, who may allow, and which phrases the stub understands. |
| 2 | [architecture.md](architecture.md) | How a turn moves through the components, which release-candidate features are under test, and how the tables relate. |
| 3 | [sequences.md](sequences.md) | The same turn drawn over time: read, pause and allow, deny, above-band approval, a rule failure, and a replayed decision. |
| 4 | [findings.md](findings.md) | What Spring AI 2.1 RC already does, and which rules this application still has to enforce. |
| 5 | [onboarding.md](onboarding.md) | How to run that story on OpenRouter, request by request, and what each response should look like. |

The repository [README](../README.md) is the short runbook: Compose, the Postman files, and `./gradlew check`. Use it when you want to start the stack before reading the design.

## How the pages fit

The hiring process is the domain. The architecture is the structure that implements it, including the Spring AI 2.1 RC pieces (session memory, tool-call confirmation, message parts, and `SessionServiceDecorator`). The sequence diagrams are that structure in time. The findings explain why some of the structure exists: the RC pauses a tool call and stores the transcript, and the salary band, the scorecard gate, the owner check, and crash recovery are still this application's job. Onboarding is the sequence diagrams as a Postman run from `01 Health` through `45 Confirm offer accepted`.

Decline, a foreign-provider `422`, and compaction are in the sequence diagrams and in onboarding. They are not part of that Postman run.

## Next to the docs

| Path | Role |
| --- | --- |
| `postman/Stateful-hiring-agent.postman_collection.json` | The ordered run described in onboarding. |
| `http/scenarios.http` | The same funnel with stub phrases. |
| `scripts/walkthrough.py` | Stub happy path from the shell. |
