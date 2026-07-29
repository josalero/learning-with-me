# How to use the MCP Integration Gateway

This guide covers the shortest runnable paths for starting the gateway, calling
tools, changing security profiles, and publishing a tool. Run all commands from
the repository root unless a section says otherwise.

**Baseline:** Java 26 · Spring Boot 4.1 · Spring AI 2.0 · Streamable HTTP MCP

## Choose a task

| Goal | Start here |
|---|---|
| Run the complete example stack | [Start with Docker Compose](#start-with-docker-compose) |
| Call the gateway from an MCP client | [Connect an MCP client](#connect-an-mcp-client) |
| Call tools through a REST API | [Run the REST facade example](#run-the-rest-facade-example) |
| Run Java processes outside containers | [Run applications with Gradle](#run-applications-with-gradle) |
| Test restricted or JWT identity | [Use security profiles](#use-security-profiles) |
| Publish an OpenAPI operation | [Publish an OpenAPI operation](#publish-an-openapi-operation) |
| Publish a SQL operation | [Publish a SQL operation](#publish-a-sql-operation) |
| Add a new connector type | [Install a connector library](#install-a-connector-library) |
| Diagnose the Compose stack | [Troubleshoot Compose](#troubleshoot-compose) |

For system design, read [Architecture](architecture.md). For a timed
walkthrough, use the [demo runbook](demo-runbook.md).

## Prerequisites

- Docker Desktop or Docker Compose with `--wait` support.
- `curl`; OpenSSL is also required for the JWT example.
- JDK 26 for Gradle commands. Confirm with `java -version`.
- Free ports `8080`, `9080`, `9081`, `9082`, and `5432`.

## Understand the default tools

The gateway exposes Streamable HTTP MCP at:

```text
http://127.0.0.1:8080/mcp
```

The default catalog publishes:

| Tool | Connector | Behavior |
|---|---|---|
| `search_candidates` | OpenAPI | Read; requires `RECRUITER` and `tools.read`; demo quota `3/1h` |
| `advance_candidate_stage` | OpenAPI | Write; requires `tools.write` and approval elicitation |
| `find_low_stock_products` | SQL | Read; executes a named parameterized `SELECT` |
| `secure_ping` | OpenAPI | Read; obtains an outbound OAuth2 client-credentials token |

Every invocation passes through argument validation, authorization, quota,
trusted-context injection, optional approval, output projection, and audit.
Sensitive arguments are hashed for audit rather than stored as raw values.

## Start with Docker Compose

```bash
docker compose up --build -d --wait
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

Expected services:

| Service | Port | Purpose |
|---|---:|---|
| `gateway` | 8080 | MCP server and governance |
| `recruiting-api` | 9080 | OpenAPI read/write target |
| `protected-api` | 9081 | OAuth2 client-credentials target |
| `postgres` | 5432 | Inventory data and JDBC audit storage |

Run the one-shot client:

```bash
docker compose --profile client run --rm --no-deps mcp-client
```

Expected highlights include four discovered tools, projected candidate output,
quota enforcement, accepted and declined writes, outbound bearer
authentication, and SQL results. The
[MCP client integration](../integrations/mcp-client/README.md) lists the exact
checks.

Stop containers while retaining database state:

```bash
docker compose down
```

Use `docker compose down -v` only when you intend to delete the local database
volume, including audit records.

## Connect an MCP client

Configure a client that supports Streamable HTTP MCP with:

```text
Base URL: http://127.0.0.1:8080
Endpoint: /mcp
```

The default `local` profile uses the synthetic identity `local-developer` in
tenant `demo-tenant`, with role `RECRUITER` and scopes `tools.read` and
`tools.write`. Never expose this unauthenticated profile outside a developer
machine.

Example arguments:

```json
{
  "skill": "Java",
  "location": "Austin",
  "limit": 5
}
```

```json
{
  "candidateReference": "CAND-1001",
  "targetStage": "PHONE_SCREEN"
}
```

```json
{
  "warehouse": "AUS-1",
  "threshold": 10
}
```

`secure_ping` takes an empty object.

Successful and failed calls use stable envelopes:

```json
{
  "ok": true,
  "data": {}
}
```

```json
{
  "ok": false,
  "error": "quota_exceeded",
  "message": "quota exceeded; retry after 3599s",
  "retryAfterSeconds": 3599
}
```

Common error categories are `validation_error`, `access_denied`,
`quota_exceeded`, `timeout`, `temporary_unavailable`, and
`connector_failure`.

To implement a Spring Boot client rather than only configure one, follow
[Build an MCP client](guides/build-mcp-client.md).

## Run the REST facade example

The orchestrator example exposes domain REST endpoints while calling the
gateway through MCP:

```text
REST caller → orchestrator API → MCP gateway → governed connector
```

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
docker compose --profile orchestrator up --build -d --wait orchestrator-api
curl -fsS http://127.0.0.1:9082/api/v1/tools
curl -fsS \
  'http://127.0.0.1:9082/api/v1/candidates/search?skill=Java&location=Austin&limit=5'
```

Recreating the gateway first resets the in-memory demo quota. See the
[REST facade integration](../integrations/rest-facade/README.md) for every
endpoint and the Gradle alternative.

## Run applications with Gradle

Start Postgres in Compose:

```bash
docker compose up -d --wait postgres
```

Then run each process from the repository root in a separate terminal:

```bash
./gradlew :example-recruiting-api:bootRun
```

```bash
./gradlew :example-protected-api:bootRun
```

```bash
./gradlew :gateway:bootRun --args='--spring.profiles.active=local'
```

```bash
./gradlew :example-mcp-client:bootRun
```

The host-side gateway expects Postgres at `127.0.0.1:5432`.

## Use security profiles

| Compose profile | Identity behavior |
|---|---|
| `docker,local` | Synthetic recruiter; `/mcp` is unauthenticated |
| `docker,local,restricted` | Synthetic viewer; restricted tools are hidden from discovery |
| `docker,jwt` | A valid inbound JWT is required on `/mcp` |

### Test a restricted identity

```bash
docker compose -f docker-compose.yml -f docker-compose.restricted.yml \
  up -d --wait --force-recreate --no-deps gateway
docker compose --profile client \
  -f docker-compose.yml -f docker-compose.restricted.yml \
  run --rm --no-deps mcp-client
```

The client should discover only `find_low_stock_products` and `secure_ping`.
Restore the default profile:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

### Test JWT authentication

```bash
docker compose -f docker-compose.yml -f docker-compose.jwt.yml \
  up -d --wait --force-recreate --no-deps gateway
TOKEN=$(./scripts/mint-demo-jwt.sh)
INITIALIZE_BODY='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
```

Without a token, expect HTTP 401:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -X POST http://127.0.0.1:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d "${INITIALIZE_BODY}"
```

With the demo token, expect an initialize result and HTTP 200:

```bash
curl -sS -w '\nHTTP %{http_code}\n' \
  -X POST http://127.0.0.1:8080/mcp \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d "${INITIALIZE_BODY}"
```

The demo maps `aud`, `tenant_id`, `roles`, and `scope` or `scopes` claims. It
uses a local HMAC secret and is not a production identity configuration.

Restore the default profile:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

## Publish an OpenAPI operation

No Java operation binding is required. The connector discovers the HTTP
method, path, parameters, JSON body, and read/write classification from an
OpenAPI document.

### 1. Define the connection

Create
`gateway/src/main/resources/gateway/catalog/<pack>/connections.yml`:

```yaml
gateway:
  connections:
    my-api:
      type: openapi
      specification: classpath:/openapi/my-api.yaml
      base-url: ${GATEWAY_CONNECTIONS_MY_API_BASE_URL:http://127.0.0.1:9090}
      authentication:
        type: none
      timeouts:
        connect: 2s
        read: 5s
```

### 2. Publish the tool

Create `gateway/src/main/resources/gateway/catalog/<pack>/tools.yml`:

```yaml
gateway:
  tools:
    my_search:
      connection: my-api
      operation: mySearchOperationId
      mode: read
      description: Search the example service.
      owner: my-platform-team
      version: "1"
      input:
        include: [query, limit]
        defaults:
          limit: 10
        override:
          limit:
            maximum: 25
      context-mappings:
        tenantId: "${identity.tenantId}"
      output:
        include: [id, name]
        maximum-items: 25
      authorization:
        required-scopes: [tools.read]
        required-roles: [EXAMPLE_READER]
      quota:
        per-subject: 100/1h
```

### 3. Import the catalog pack

Add both resources under `spring.config.import` in
`gateway/src/main/resources/application.yml`:

```yaml
spring.config.import:
  - optional:classpath:gateway/catalog/<pack>/connections.yml
  - optional:classpath:gateway/catalog/<pack>/tools.yml
```

### 4. Restart and inspect

```bash
docker compose build gateway
docker compose up -d --wait --force-recreate --no-deps gateway
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog
```

Startup fails when a catalog tries to publish trusted context as model input,
widen an OpenAPI constraint, declare a mutating operation as a read, or
reference an unknown connection or operation.

To add configuration without rebuilding the image, use the
[external catalog sample](../samples/catalogs/external/README.md). Catalog
changes
still require a gateway restart.

## Publish a SQL operation

1. Define a `type: sql` connection with a datasource bean name.
2. Define a named, parameterized `SELECT` operation with `max-rows` and a
   timeout.
3. Publish a tool that references the connection and operation.
4. Keep the output allowlist narrow.

Use the
[`inventory` catalog pack](../gateway/src/main/resources/gateway/catalog/inventory/)
as a complete example. The validator rejects mutations and multiple
statements. JDBC applies the row limit and query timeout before returning the
result.

## Install a connector library

Connector JARs register through Spring Boot auto-configuration and may use any
Java package. Add the library to the gateway as a `runtimeOnly` dependency,
provide matching catalog entries, and restart.

Follow [Build a connector library](guides/build-connector.md) for the complete
contract, testing, publication, and installation workflow. The
[Echo connector](../connectors/samples/echo/README.md) is the minimal working
example.

## Configure governance

| Concern | Catalog field | Behavior |
|---|---|---|
| Model-visible arguments | `input.include`, `defaults`, `override` | Projects and narrows the discovered operation schema |
| Trusted context | `context-mappings` | Injects server-controlled identity values after validation |
| Output data | `output.include`, `mask`, `maximum-items` | Removes, masks, and limits result fields |
| Authorization | `authorization.required-scopes`, `required-roles` | Enforces all scopes and at least one configured role |
| Quota | `quota.per-subject` | Limits calls per subject in the current JVM |
| Approval | `requires-approval` | Requires MCP elicitation before execution |
| Audit metadata | `owner`, `version`, `deprecated` | Identifies the published tool for operators |

The [catalog reference](reference/catalog.md) is the source of truth for every
field and validation rule.

Inspect the sanitized compiled catalog:

```bash
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog
```

Inspect recent audit outcomes:

```bash
docker compose exec postgres \
  psql -U gateway -d mcp_gateway \
  -c 'select tool_name, subject, result_category, removed_field_count from tool_invocation_audit order by id desc limit 10;'
```

## Test downstream failures

Force the recruiting API to return HTTP 500:

```bash
RECRUITING_CHAOS_FORCE_STATUS=500 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

Force a delay beyond the gateway's five-second read timeout:

```bash
RECRUITING_CHAOS_DELAY_MS=8000 RECRUITING_CHAOS_FORCE_STATUS=0 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

Restore normal behavior:

```bash
RECRUITING_CHAOS_DELAY_MS=0 RECRUITING_CHAOS_FORCE_STATUS=0 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

Recreate the gateway between repeat client runs to reset the in-memory demo
quota. The [Recruiting API sample](../samples/backends/recruiting-api/README.md)
documents its failure controls.

## Troubleshoot Compose

- Always use `--no-deps` when running the one-shot client or replacing only the
  gateway. Otherwise Compose may recreate dependencies from the base file and
  drop an active override.
- Recreate the gateway before repeat demos because quota state is in memory.
- The Postgres 18 volume mounts at `/var/lib/postgresql`, not
  `/var/lib/postgresql/data`.
- The first image build downloads Gradle and container layers; later builds use
  the cache.

See the [Docker Compose reference](../docker/README.md) for service, override,
inspection, and reset commands.

## Validate the repository

Run the unit and integration test suite:

```bash
./gradlew test publishToMavenLocal
```

Run the repeatable end-to-end evidence workflow:

```bash
.agents/skills/mcp-gateway-qa-agent/scripts/verify.sh full
```

Use the [documentation guide](README.md) to continue with architecture,
application integration, connector development, operations, and limitations.
