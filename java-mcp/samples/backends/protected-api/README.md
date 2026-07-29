# Protected API example

This synthetic service demonstrates a gateway calling a downstream API with
OAuth2 client credentials. It exposes:

- `POST /oauth/token`
- `GET /api/secure/ping`
- `GET /actuator/health`

The credentials and token are fixed local-demo values, not production secrets.

## Run

Run from the repository root. With Compose:

```bash
docker compose up --build -d --wait protected-api
curl -fsS http://127.0.0.1:9081/actuator/health
```

Or directly with Gradle:

```bash
./gradlew :example-protected-api:bootRun
```

## Exercise the OAuth flow

Request the demo token:

```bash
curl -fsS -X POST http://127.0.0.1:9081/oauth/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=gateway-client&client_secret=gateway-secret'
```

Call the protected endpoint with the returned fixed token:

```bash
curl -fsS http://127.0.0.1:9081/api/secure/ping \
  -H 'Authorization: Bearer demo-access-token'
```

The gateway performs this flow when the `secure_ping` MCP tool is called. Its
OpenAPI contract is
[`openapi/protected.yaml`](../../../gateway/src/main/resources/openapi/protected.yaml).
