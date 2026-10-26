# Documentation

Read these in order. Each page assumes the one before it.

| Order | Page | Question it answers |
| --- | --- | --- |
| 1 | [hiring-process.md](hiring-process.md) | The goal, the agent loop, the workflow at each stage, who may ask, who may allow, and which phrases the stub understands. |
| 2 | [turn.md](turn.md) | What one turn sends and returns, and where that shows up in the HTTP body, the log, the traces, and the GETs. |
| 3 | [architecture.md](architecture.md) | How a turn moves through the components, which release-candidate features are under test, and how the tables relate. |
| 4 | [sequences.md](sequences.md) | How the session, the conversation, and the job application stay linked, then the same turn in each scenario. |
| 5 | [findings.md](findings.md) | What Spring AI 2.1 RC already does, and which rules this application still has to enforce. |
| 6 | [onboarding.md](onboarding.md) | How to run that story on OpenRouter, request by request, and what each response should look like. |

The repository [README](../README.md) is the short runbook: Compose, the Postman files, and `./gradlew check`. Use it when you want to start the stack before reading the design.

## How the pages fit

The hiring process is the domain. [turn.md](turn.md) is one session in three calls: the text that goes in, the status that comes back, and the log line, span, and GET that confirm it. The architecture is the structure that implements it, including the Spring AI 2.1 RC pieces (session memory, tool-call confirmation, message parts, and `SessionServiceDecorator`). The sequence diagrams are that structure in time, starting with how the session, the conversation log, and the job application point at each other. The findings explain why some of the structure exists: the RC pauses a tool call and stores the transcript, and the salary band, the scorecard gate, the owner check, and crash recovery are still this application's job. Onboarding is the sequence diagrams as a Postman run from `01 Health` through `45 Confirm offer accepted`.

Decline, a foreign-provider `422`, and compaction are in the sequence diagrams and in onboarding. They are not part of that Postman run.

## Next to the docs

| Path | Role |
| --- | --- |
| `postman/Stateful-hiring-agent.postman_collection.json` | The ordered run described in onboarding. |
| `http/scenarios.http` | The same funnel with stub phrases. |
| `scripts/walkthrough.py` | Stub happy path from the shell. |
