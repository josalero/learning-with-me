# Recruiting API example

This synthetic REST service is the downstream target for the gateway’s
OpenAPI tools:

- `search_candidates`
- `advance_candidate_stage`

It intentionally contains sensitive candidate fields and an untrusted notes
field so the gateway can demonstrate trusted-context injection and output
allowlisting. Do not copy its data model or unauthenticated endpoints into a
production service.

## Run

Run from the repository root. With Compose:

```bash
docker compose up --build -d --wait recruiting-api
curl -fsS http://127.0.0.1:9080/actuator/health
```

Or directly with Gradle:

```bash
./gradlew :example-recruiting-api:bootRun
```

## Call the downstream directly

Search:

```bash
curl -fsS -G http://127.0.0.1:9080/api/candidates \
  --data-urlencode 'skill=Java' \
  --data-urlencode 'location=Austin' \
  --data-urlencode 'tenantId=demo-tenant'
```

Advance a stage:

```bash
curl -fsS -X POST \
  'http://127.0.0.1:9080/api/candidates/CAND-1001/stage?tenantId=demo-tenant&targetStage=PHONE_SCREEN&requestedBy=developer'
```

Direct search responses contain synthetic personally identifiable information
(PII). Calls through the gateway return only the fields allowed by the catalog.

## Failure injection

The Compose service accepts:

- `RECRUITING_CHAOS_FORCE_STATUS`, such as `500`
- `RECRUITING_CHAOS_DELAY_MS`, such as `8000`

Example:

```bash
RECRUITING_CHAOS_FORCE_STATUS=500 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

Restore it with:

```bash
RECRUITING_CHAOS_FORCE_STATUS=0 RECRUITING_CHAOS_DELAY_MS=0 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

The gateway contract is defined in
[`openapi/recruiting.yaml`](../../../gateway/src/main/resources/openapi/recruiting.yaml).
