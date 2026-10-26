# Findings

Read this after [architecture.md](architecture.md). That page shows where each release-candidate feature sits in the components. This page is what those features do not cover.

This POC sits on Spring Boot 4.2.0-RC1 and Spring AI 2.1.0-RC1 because that is the release that ships session memory, tool-call confirmation, and message parts together. Those APIs are pre-release. The stable Spring AI 2.0 line does not have them.

## What the framework already did

- Append-only session events and an active window with compaction.
- Pausing a turn when a tool is marked for confirmation, and resuming from the stored assistant message.
- Decorating `SessionService` without replacing the auto-configured bean.

## What the application still has to enforce

- The confirmation policy can only add confirmations. It cannot know the salary band until the tenant is known, so every write is paused and the real risk is stored on the approval row.
- The tool context does not include the current tool-call id. The idempotency key is a hash of the canonical arguments, mapped back to the call key that was stored at pause time.
- Session metadata cannot be updated, so provider and compatibility mode live in `agent_session`.
- A crash can leave `EXECUTING`. Recovery compares `hiring_action` and does not resume the model.
- Evaluation after the turn checks that a hire has an accepted offer and that a gated advance has a passing scorecard. It logs a warning instead of hiding the user's answer.

## Access choice

Bob can approve a high-risk action and still cannot read Alice's transcript. The decision endpoint is the only shared door, and a foreign tenant still looks like a missing session.

## Left out

The list in [hiring-process.md](hiring-process.md) is the product boundary. This service does not send mail, generate an offer letter, or talk to another hiring system.
