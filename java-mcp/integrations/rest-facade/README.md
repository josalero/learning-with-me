# REST orchestrator example

This application exposes ordinary domain REST endpoints while using the gateway
as an MCP client internally. It demonstrates how an application can adopt
governed tools without exposing MCP concepts to its callers.

```text
REST caller → orchestrator API → MCP gateway → governed connector
```

## Run

Run from the repository root. Start the default stack. If the one-shot MCP
client was already run, recreate the gateway first to reset its in-memory demo
quota:

```bash
docker compose up --build -d --wait
docker compose up -d --wait --force-recreate --no-deps gateway
docker compose --profile orchestrator up --build -d --wait orchestrator-api
```

## Call the REST facade

```bash
curl -fsS http://127.0.0.1:9082/actuator/health
curl -fsS http://127.0.0.1:9082/api/v1/tools
curl -fsS 'http://127.0.0.1:9082/api/v1/candidates/search?skill=Java&location=Austin&limit=5'
curl -fsS 'http://127.0.0.1:9082/api/v1/inventory/low-stock?warehouse=AUS-1&threshold=10'
curl -fsS http://127.0.0.1:9082/api/v1/secure/ping
curl -fsS -X POST \
  http://127.0.0.1:9082/api/v1/candidates/CAND-1001/stage \
  -H 'Content-Type: application/json' \
  -H 'X-Approve: true' \
  -d '{"targetStage":"PHONE_SCREEN"}'
```

Set `X-Approve: false` to exercise the declined write path.

## Run from Gradle

With the gateway already available at `http://127.0.0.1:8080`:

```bash
./gradlew :example-orchestrator-api:bootRun
```

The REST facade is intentionally unauthenticated for local demonstration. A
production facade must authenticate callers and propagate their identity to a
JWT-secured gateway rather than using the gateway’s synthetic identity.
