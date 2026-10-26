# Onboarding

Use Postman against the Compose stack, with OpenRouter as the model. The suggested model is `openai/gpt-4o-mini`. It calls tools reliably enough for this short funnel and stays cheap. The app already points OpenRouter at that slug. If a turn comes back as prose and never pauses for a tool, change `OPENROUTER_CHAT_MODEL` to `openai/gpt-4o` and start a new session.

The model's sentences vary. Judge a turn by the HTTP status, `status`, the tool name, and `risk`. Confirm the stage with `GET /api/v1/applications/APP-1` after each allow. A new message while an approval is still `PENDING` returns `409`. Allow or deny that approval before you send the next message.

## What you need

- Docker.
- Postman, the app or the `postman` CLI.
- An OpenRouter API key from [https://openrouter.ai/keys](https://openrouter.ai/keys).
- JDK 25 (`jenv shell 25`) only for `./gradlew check` or `bootRun` on the host.

## Start the API on OpenRouter

```bash
cp .env.example .env
```

Put your key and the local passwords in `.env`, and turn the profile on:

```bash
SPRING_PROFILES_ACTIVE=openrouter
OPENROUTER_API_KEY=sk-or-...
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_CHAT_MODEL=openai/gpt-4o-mini
```

`openrouter` is the profile that creates the OpenAI-compatible client. Without it, `POST /api/v1/sessions` with `"provider": "OPENROUTER"` returns `503`. The key stays in `.env`. Do not paste it into Postman.

```bash
docker compose up --build
```

| Port | Process |
| --- | --- |
| 8080 | Agent API. Postman `baseUrl`. |
| 5433 | Postgres on the host. The container listens on 5432. |
| 4318 | OTLP HTTP |
| 3000 | Grafana |

Leave `POSTGRES_PORT` at 5433. A sibling project uses host port 5432.

`docker compose down -v` drops the database. `APP-1` is back at recruiter screen only after that. The happy path hires `APP-1`, so run it once per fresh volume.

## Postman

Import these two files with **Import → File**:

- `postman/Stateful-hiring-agent.postman_collection.json`
- `postman/Local.postman_environment.json`

Select the **Local** environment. The collection is a single list, numbered `01` through `45`. That order is the run order. Do not reorder the requests, and do not run one request from the middle on a fresh session: each step stores the session id or the approval id for the next one.

Open the collection variables and set `alicePassword`, `bobPassword`, `carolPassword`, and `davePassword` to the `AGENT_*_PASSWORD` values in your `.env`. The placeholders match `.env.example`. `provider` is already `OPENROUTER`.

Then use the Collection Runner on **Stateful hiring agent** and run the whole collection from the top. It needs a fresh database (`docker compose down -v`, then `up` again) because `APP-1` is hired at step 44. Run it once per volume.

What the run does, in order:

| Steps | What you should see |
| --- | --- |
| 01–04 | Health is up. Alice's session is `OPENROUTER`. `APP-1` is `RECRUITER_SCREEN`. |
| 05–07 | Scheduling pauses (`202`, medium). Denying it leaves the stage unchanged. |
| 08–13 | The second schedule is allowed. Tech screen `YES` is stored. |
| 14–19 | Alice cannot reject. Bob, Carol, and Dave get `404` on her session. A bad id is `400`. |
| 20–23 | Bob moves `APP-1` to `ONSITE`. That pause is high, and he allows it. |
| 24–28 | An onsite `NO` blocks the move to `OFFER`. The stage stays `ONSITE`. |
| 29–34 | `STRONG_YES` clears the gate. The stage becomes `OFFER`. There is no offer row yet. |
| 35–37 | Salary `90000` is allowed through and then fails. Still no offer. |
| 38–41 | Salary `190000` is high. Alice gets `403`. Bob allows it. Offer status is `EXTENDED`. |
| 42–45 | Acceptance is allowed. Stage is `HIRED`. Offer status is `ACCEPTED`. |

The model's sentence in `answer` can vary. The tests check the HTTP status, `status`, the tool name, `risk`, and the application reads.

If a message step returns `200` with prose and no approval, the model did not call the tool. Stop the runner. The following allow request would use a stale approval id. Start a new session only after resetting the volume.

```bash
postman collection run postman/Stateful-hiring-agent.postman_collection.json \
  -e postman/Local.postman_environment.json
```

Basic auth on the collection is user `alice` and `{{alicePassword}}`. Bob's requests override that with user `bob`.

Create-session responses look like this. The test script stores `id` as `aliceSessionId` or `bobSessionId`.

```http
HTTP/1.1 201 Created
```

```json
{
  "id": "3f1c2a0e-7b44-4d1a-9c2e-0a6b8d4e1f90",
  "provider": "OPENROUTER",
  "compatibilityMode": "REJECT"
}
```

A write that needs a person looks like this. The test script stores `approval.id` and `calls[0].toolCallKey`.

```http
HTTP/1.1 202 Accepted
```

```json
{
  "status": "AWAITING_APPROVAL",
  "answer": null,
  "approval": {
    "id": "6b1e0c3a-1111-4222-8333-444455556666",
    "status": "PENDING",
    "risk": "MEDIUM",
    "calls": [
      {
        "toolCallKey": "call_abc",
        "toolName": "advanceApplication",
        "decision": "PENDING"
      }
    ]
  }
}
```

Allow it with the same person who owns the session, unless the risk is `HIGH`. A high-risk allow must be Bob. Send a new `Idempotency-Key` every time. The same key returns this stored body again. A different key after the decision returns `409`.

```http
POST /api/v1/sessions/{{aliceSessionId}}/approvals/{{approvalId}}/decision
Authorization: Basic alice / {{alicePassword}}
Idempotency-Key: 4f0c2d44-9a1b-4e3c-8d55-1c2b3a4d5e6f
Content-Type: application/json
```

```json
{
  "decisions": [
    { "toolCallKey": "{{toolCallKey}}", "decision": "ALLOW" }
  ]
}
```

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "COMPLETED",
  "answer": "The tech screen is scheduled. APP-1 is now at TECH_SCREEN.",
  "approval": {
    "id": "6b1e0c3a-1111-4222-8333-444455556666",
    "status": "COMPLETED",
    "risk": "MEDIUM",
    "calls": [
      {
        "toolCallKey": "call_abc",
        "toolName": "advanceApplication",
        "decision": "ALLOW"
      }
    ]
  }
}
```

`answer` is the model's wording. `status` is `COMPLETED` when the tool was applied.

The sections below explain the same story the collection runs. The requests to execute are the numbered list in the collection, from `01 Health` through `45 Confirm offer accepted`.

## Seeded applications

| Id | Tenant | Role | Stage | Band |
| --- | --- | --- | --- | --- |
| APP-1 | tenant-a | Backend Engineer | RECRUITER_SCREEN | 120000–160000 |
| APP-2 | tenant-b | Support Engineer | APPLIED | 70000–90000 |

Alice and Bob are `tenant-a`. Carol is `tenant-b`. Dave is `tenant-a` and is neither the owner nor an approver. There is no real candidate data.

| User | Password variable | What they can do |
| --- | --- | --- |
| alice | `alicePassword` | Read, schedule, scorecards, in-band offers, offer responses. |
| bob | `bobPassword` | Those, plus onsite, offer stage, reject, and above-band allow. He cannot read Alice's session. |
| carol | `carolPassword` | Her own tenant. Alice's session and `APP-1` are `404`. |
| dave | `davePassword` | `404` on a session he does not own. |

## Same story as the collection

The Collection Runner already sends these requests, in number order, on one fresh `APP-1`. The headings below name those request numbers. Do not run a section a second time after step 44 has hired the application.

An in-band salary of `150000` is a valid offer, and the stub phrases in [hiring-process.md](hiring-process.md) use it. The collection does not. It denies the first schedule, records an onsite `NO` before `STRONG_YES`, fails `90000`, then has Bob allow `190000`.

## Requests 01–13 — Open the session and reach the tech screen

Auth is Alice unless the step says Bob.

### 1. Health

`GET {{baseUrl}}/actuator/health` with no auth.

```http
HTTP/1.1 200 OK
```

```json
{ "status": "UP" }
```

### 2. Alice opens a session

`POST {{baseUrl}}/api/v1/sessions`

```json
{ "provider": "OPENROUTER" }
```

Expect `201` and `"provider": "OPENROUTER"`. Copy `id` into `aliceSessionId` if you are not using the runner.

### 3. Read the stage

`POST {{baseUrl}}/api/v1/sessions/{{aliceSessionId}}/messages`

```json
{ "text": "What is the current stage of application APP-1? Use the getApplication tool and quote the stage." }
```

Expect `200` and `"status": "COMPLETED"`. There is no `approval`. The answer names `RECRUITER_SCREEN`.

```http
GET {{baseUrl}}/api/v1/applications/APP-1
```

```json
{
  "id": "APP-1",
  "roleTitle": "Backend Engineer",
  "stage": "RECRUITER_SCREEN",
  "bandMin": "120000.00",
  "bandMax": "160000.00"
}
```

### 4. Schedule the tech screen

```json
{ "text": "Move application APP-1 to the TECH_SCREEN stage. Call advanceApplication. Do not invent a stage." }
```

Expect `202`, `"status": "AWAITING_APPROVAL"`, `"risk": "MEDIUM"`, `"toolName": "advanceApplication"`.

`GET /api/v1/applications/APP-1` is still `RECRUITER_SCREEN`. No hiring action exists yet.

### 5. Alice allows it

Send the allow body from the top of this page, as Alice, with a new `Idempotency-Key`.

Expect `200` and `"status": "COMPLETED"`. Then:

```json
{ "id": "APP-1", "stage": "TECH_SCREEN" }
```

### 6. Record a passing tech screen

```json
{ "text": "Record a TECH_SCREEN scorecard for APP-1 with recommendation YES. Call recordScorecard. Do not invent the recommendation." }
```

Expect `202`, risk `MEDIUM`, tool `recordScorecard`. Allow it as Alice.

Expect `200` `COMPLETED`. The stage stays `TECH_SCREEN`. The scorecard list is:

`GET {{baseUrl}}/api/v1/applications/APP-1/scorecards`

```json
[
  { "round": "TECH_SCREEN", "recommendation": "YES", "score": 3 }
]
```

`YES` maps to 3 on each dimension. `STRONG_YES` would be 4.

### 7. Bob opens his own session

Switch auth to Bob. `POST {{baseUrl}}/api/v1/sessions`

```json
{ "provider": "OPENROUTER" }
```

Expect `201`. Store `id` as `bobSessionId`.

Bob cannot read Alice's transcript:

`GET {{baseUrl}}/api/v1/sessions/{{aliceSessionId}}` as Bob.

```http
HTTP/1.1 404 Not Found
```

### 8. Advance to onsite

Still Bob, on his session.

```json
{ "text": "Advance application APP-1 to ONSITE. Call advanceApplication." }
```

Expect `202`, `"risk": "HIGH"`, tool `advanceApplication`. Alice is not an approver, so she cannot allow this. Allow it as Bob.

Expect `200` `COMPLETED`. `GET` the application as Alice or Bob: `"stage": "ONSITE"`.

### 9. Onsite debrief

Bob:

```json
{ "text": "Record an ONSITE scorecard for APP-1 with recommendation STRONG_YES. Call recordScorecard." }
```

Expect `202`, risk `MEDIUM`. Allow as Bob.

Expect `200` `COMPLETED`. Stage stays `ONSITE`. Scorecards now include `ONSITE` / `STRONG_YES` / score `4`.

### 10. Advance to offer

Bob:

```json
{ "text": "Advance application APP-1 to OFFER. Call advanceApplication." }
```

Expect `202`, risk `HIGH`. Allow as Bob.

Expect `200` `COMPLETED` and `"stage": "OFFER"`. There is still no offer row. `GET /api/v1/applications/APP-1/offer` is `404`.

### 11. Extend the offer

The collection does this as requests 35–41, after the stage is `OFFER` and `GET .../offer` is still `404`.

Alice first sends salary `90000`. That pauses at medium risk. Her allow returns `200` with `"status": "FAILED"`. The answer contains `OFFER_OUT_OF_BAND`. The offer read stays `404`.

She then sends salary `190000`. That pauses at high risk. Her allow is `403`. The approval stays pending. Bob allows the same approval id and tool-call key with a new `Idempotency-Key` and gets `200` `COMPLETED`.

```json
{
  "level": "L4",
  "baseSalary": "190000",
  "status": "EXTENDED"
}
```

Salary formatting may include a decimal. `status` is `EXTENDED`. A second extend on `APP-1` fails because an offer is already there.

### 12. Candidate accepts

Alice:

```json
{ "text": "The candidate accepted the offer for APP-1. Call recordOfferResponse with ACCEPTED." }
```

Expect `202`, risk `MEDIUM`, tool `recordOfferResponse`. Allow as Alice.

Expect `200` `COMPLETED`.

```json
{ "id": "APP-1", "stage": "HIRED" }
```

```json
{ "status": "ACCEPTED" }
```

`GET /api/v1/applications/APP-1/actions` returns the advances, the scorecards' action rows, the offer, and the acceptance. The list is non-empty.

`GET /api/v1/sessions/{{aliceSessionId}}/events?view=all` as Alice returns the full log, including `AGENT.APPROVAL_REQUESTED`, `AGENT.APPROVAL_DECIDED`, and `AGENT.TOOL_EXECUTED`. `view=active` is the window the model sees and is shorter once compaction has run. With the default of 20 turns it has not run yet. See the compaction scenario.

## Requests 05–07 — Deny the first schedule

This happens before the successful schedule in the collection. A deny does not move the stage, so request 08 can schedule again.

Alice, new `OPENROUTER` session.

```json
{ "text": "Move application APP-1 to the TECH_SCREEN stage. Call advanceApplication." }
```

Expect `202` and risk `MEDIUM`. Then deny:

```json
{
  "decisions": [
    {
      "toolCallKey": "{{toolCallKey}}",
      "decision": "DENY",
      "reason": "Not now"
    }
  ]
}
```

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "DENIED",
  "approval": { "status": "DENIED" }
}
```

The answer says the schedule was not applied. `GET /api/v1/applications/APP-1` is still `RECRUITER_SCREEN`. `GET .../actions` is empty.

## Requests 14–15 — Alice tries to reject

This is after the tech screen in the collection. She is not an approver, so the agent does not pause.

```json
{ "text": "Reject application APP-1 for reason BAR_NOT_MET. Call rejectApplication." }
```

```http
HTTP/1.1 200 OK
```

```json
{ "status": "DENIED" }
```

No hiring action is written. In the collection the stage stays `TECH_SCREEN`. If you send this before any advance, it stays `RECRUITER_SCREEN`.

## Requests 16–19 — Who can see the session

Using Alice's `aliceSessionId`:

| Auth | Request | Expect |
| --- | --- | --- |
| bob | `GET /api/v1/sessions/{{aliceSessionId}}` | `404` |
| carol | same | `404` |
| dave | same | `404` |
| alice | `GET /api/v1/sessions/not-a-uuid` | `400` |
| carol | `GET /api/v1/applications/APP-1` | `404` |

Bob can still `POST` a decision on Alice's approval when the risk is `HIGH`. He cannot read the transcript.

## Requests 38–41 — Above the band

Do this while `APP-1` is at `OFFER` and the below-band attempt has already failed, so there is still no offer row. One application has one offer, and the successful `190000` write is the one the collection keeps.

Alice:

```json
{ "text": "Extend an offer for APP-1 at level L4 with base salary 190000. Call extendOffer." }
```

Expect `202`, `"risk": "HIGH"`, tool `extendOffer`. `190000` is above `160000`.

Alice's allow, with her own `Idempotency-Key`:

```http
HTTP/1.1 403 Forbidden
```

The approval stays `PENDING`. Bob allows the same approval id and tool-call key, with a different `Idempotency-Key`:

```http
HTTP/1.1 200 OK
```

```json
{ "status": "COMPLETED" }
```

The offer exists. A second extend on `APP-1` fails because an offer is already there.

## Requests 35–37 — Below the band

The collection sends this before the above-band salary, while `APP-1` is at `OFFER` and has no offer row.

Alice:

```json
{ "text": "Extend an offer for APP-1 at level L4 with base salary 90000. Call extendOffer." }
```

Expect `202` and risk `MEDIUM`. `90000` is under `120000`, and that check happens when the tool runs, not when the pause is created. Allow as Alice.

```http
HTTP/1.1 200 OK
```

```json
{ "status": "FAILED" }
```

The answer says the salary is outside the band. The tool error is `OFFER_OUT_OF_BAND`. `GET .../offer` is `404`. The stage stays `OFFER`.

## Requests 24–34 — A No blocks the offer, then a later card passes

The collection records onsite `NO`, allows the blocked advance, then records `STRONG_YES` and advances to `OFFER`. The latest card for the round is the one the gate uses.

Bob:

```json
{ "text": "Record an ONSITE scorecard for APP-1 with recommendation NO. Call recordScorecard." }
```

Expect `202`, risk `MEDIUM`. Allow as Bob. Expect `200` `COMPLETED`. Stage stays `ONSITE`. The card is `NO` with score `2`.

Bob:

```json
{ "text": "Advance application APP-1 to OFFER. Call advanceApplication." }
```

Expect `202`, risk `HIGH`. Allow as Bob.

```json
{ "status": "FAILED" }
```

The answer says the onsite scorecard is not passing. The tool error is `SCORECARD_NOT_PASSING`. The stage stays `ONSITE`.

Requests 29–34 then record onsite `STRONG_YES` and advance again. The latest card for `ONSITE` is the one that counts, so the second allow returns `200` `COMPLETED` and the stage becomes `OFFER`. `GET .../offer` is still `404`.

## Not in the collection — Candidate declines

The runner ends at `HIRED`, so it does not decline. To try this path, use a fresh volume, reach `OFFER`, extend an in-band salary such as `150000`, and allow it. Then Alice:

```json
{ "text": "The candidate declined the offer for APP-1. Call recordOfferResponse with DECLINED." }
```

Expect `202`, risk `MEDIUM`. Allow as Alice.

```json
{ "id": "APP-1", "stage": "OFFER_DECLINED" }
```

The offer `status` is `DECLINED`.

Bob, afterwards:

```json
{ "text": "Reject application APP-1 for reason BAR_NOT_MET. Call rejectApplication." }
```

He can request it because he is an approver. Expect `202`, risk `HIGH`. His allow returns `200` with `"status": "FAILED"`. The stage stays `OFFER_DECLINED`. A closed application is not rejected again.

## Not in the collection — Incompatible provider parts

Leave this out of the OpenRouter run. It checks that a session stored by another provider cannot be replayed into the stub unchanged.

Alice. `POST /api/v1/sessions`

```json
{ "provider": "STUB_FOREIGN", "compatibilityMode": "REJECT" }
```

Expect `201`. Then:

```json
{ "text": "hello" }
```

Expect `200` `COMPLETED`. The event log for `view=all` includes a part whose provider is `stub-foreign`.

`PATCH /api/v1/sessions/{{foreignSessionId}}`

```json
{ "provider": "STUB", "compatibilityMode": "REJECT" }
```

Expect `200` and `"provider": "STUB"`. Next message:

```json
{ "text": "note: after switch" }
```

```http
HTTP/1.1 422 Unprocessable Content
```

The problem body has type `provider-incompatible`. To keep going, create the session with `"compatibilityMode": "STRIP_PROVIDER_PAYLOAD"`, or patch that mode before the next message. The following turn then returns `200`, and the model sees a text placeholder instead of the foreign part. The full event log still has the original part.

## Not in the collection — Compaction

The Compose default keeps 20 turns and about 8000 tokens. Four notes do not compact. For a short demo, add this to `.env` and recreate the agent container:

```bash
AGENT_SESSION_COMPACTION_MAX_TURNS=3
AGENT_SESSION_COMPACTION_PRESERVE_TURNS=2
```

Alice, `OPENROUTER` session. Send four messages:

```json
{ "text": "note: the candidate clarified their Java years. Remember this. Turn 1." }
```

Repeat with turns 2, 3, and 4. Each one expects `200` and `"status": "COMPLETED"`, and no approval.

Then `GET /api/v1/sessions/{{aliceSessionId}}/events?view=all`.

The list includes an event whose `type` contains `COMPACT`, and the earlier user messages are still in this full log. `view=active` is shorter and starts at a user turn inside the kept window.

Put the limits back to 20 and 8 when you are done. The happy path does not need them.

## Traces

After any allowed turn, open [http://localhost:3000](http://localhost:3000) and look for `agent.turn` and `agent.approval.decide`. The decision span links to the trace id stored on the approval when the turn paused.

## Host process

Postman still uses `http://localhost:8080`.

```bash
docker compose up postgres otel
set -a && source .env && set +a
jenv shell 25
./gradlew bootRun
```

`OTEL_EXPORTER_OTLP_ENDPOINT` is the collector base, `http://localhost:4318` on the host and `http://otel:4318` in Compose. Boot adds `/v1/traces` and `/v1/metrics`. `SPRING_PROFILES_ACTIVE=openrouter` must be exported with the key, the same as in Compose.
