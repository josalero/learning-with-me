# Stateful hiring agent

A small hiring assistant that keeps a durable conversation, pauses before it changes an application, and resumes after a person allows or denies the tool call.

The demo domain is a tech screen and an offer. The model used by the automated tests and by Compose, unless you set a live API key and switch the session, is a deterministic stub. It understands a fixed set of phrases listed in [docs/hiring-process.md](docs/hiring-process.md).

## Run the scenario

```bash
cp .env.example .env
docker compose up --build
```

Import `postman/Stateful-hiring-agent.postman_collection.json` and `postman/Local.postman_environment.json`. Select **Local**, set the four password collection variables from `.env`, and set `SPRING_PROFILES_ACTIVE=openrouter` plus `OPENROUTER_API_KEY` before `docker compose up --build`. The suggested model is `openai/gpt-4o-mini`. Run the collection from `01 Health` through `45 Confirm offer accepted` on a fresh volume (`docker compose down -v` first). The request order and expected responses are in [docs/onboarding.md](docs/onboarding.md).

Grafana is at [http://localhost:3000](http://localhost:3000). Postgres is published on host port `5433`.

## What the collection proves

Run `01 Health` through `45 Confirm offer accepted` once on a fresh volume.

- A status question returns the stage and does not create an approval.
- The first tech-screen schedule returns `202`. Denying it leaves `APP-1` at `RECRUITER_SCREEN`. Allowing the second schedule moves it to `TECH_SCREEN`.
- Alice cannot reject. Bob, Carol, and Dave receive `404` on her session. A malformed session id is `400`.
- Bob requests and allows the onsite move and the offer move. An onsite `NO` makes the offer advance `FAILED`; a later `STRONG_YES` lets it through.
- Salary `90000` fails with `OFFER_OUT_OF_BAND`. Salary `190000` is high: Alice receives `403`, Bob allows it, and acceptance moves the application to `HIRED`.
- Traces for `agent.turn` and `agent.approval.decide` are exported to the collector started by Compose.

Decline, a foreign-provider `422`, and compaction are documented in [docs/onboarding.md](docs/onboarding.md) and are not part of that run. The stub phrases and the scenario tests cover them.

## Layout

| Path | Role |
| --- | --- |
| `src/main/java/.../business` | Stages, scorecards, offers |
| `src/main/java/.../agent` | Turn loop and stub model |
| `src/main/java/.../approval` | Pause, decision, recovery |
| `src/main/java/.../session` | Owner checks and provider cleanup |
| `docs/` | Index, process, architecture, sequences, findings, onboarding |

Start at [docs/README.md](docs/README.md). That index is the reading order for the process, the architecture, the sequence diagrams, the release-candidate findings, and the Postman walkthrough.

## Tests

```bash
jenv shell 25
./gradlew check
```

`check` needs Docker because the scenario tests start PostgreSQL with Testcontainers. Live provider tests are tagged `live` and are not part of `check`.
