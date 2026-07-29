# Docker Compose reference

Run every command on this page from the repository root. For a guided first
run, use [How to use the gateway](../docs/HOW-TO-USE.md).

The default stack packages the gateway with the OpenAPI and SQL connectors,
JDBC audit storage, two sample APIs, and Postgres.

## Services

| Service | Port | Starts by default | Purpose |
|---|---:|---|---|
| `gateway` | 8080 | Yes | MCP endpoint and operational endpoints |
| `recruiting-api` | 9080 | Yes | OpenAPI read/write example |
| `protected-api` | 9081 | Yes | Outbound bearer-authentication example |
| `postgres` | 5432 | Yes | SQL example and JDBC audit storage |
| `mcp-client` | — | `client` profile | One-shot MCP discovery and invocation client |
| `orchestrator-api` | 9082 | `orchestrator` profile | Domain REST facade backed by MCP |

## Start the default stack

```bash
docker compose up --build -d --wait
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
```

Run the one-shot MCP client:

```bash
docker compose --profile client run --rm --no-deps mcp-client
```

Start the optional REST facade:

```bash
docker compose --profile orchestrator up --build -d --wait orchestrator-api
curl -fsS http://127.0.0.1:9082/api/v1/tools
```

## Compose overrides

Combine the base file with one override at a time:

| Override | Purpose | Related example |
|---|---|---|
| `docker-compose.restricted.yml` | Run with a restricted synthetic identity. | [MCP client](../integrations/mcp-client/README.md) |
| `docker-compose.jwt.yml` | Require an inbound demo JWT. | [MCP client](../integrations/mcp-client/README.md) |
| `docker-compose.catalog.yml` | Mount an external catalog. | [External catalog](../samples/catalogs/external/README.md) |
| `docker-compose.chaos.yml` | Inject downstream delay or failure. | [Recruiting API](../samples/backends/recruiting-api/README.md) |

Example: switch only the running gateway to the JWT override:

```bash
docker compose -f docker-compose.yml -f docker-compose.jwt.yml \
  up -d --wait --force-recreate --no-deps gateway
./scripts/mint-demo-jwt.sh
```

Restore the default gateway:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

`--no-deps` prevents Compose from recreating healthy dependencies while
switching gateway profiles.

## Inspect the running system

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog
docker compose logs --tail=200 gateway
```

Inspect recent audit outcomes:

```bash
docker compose exec postgres \
  psql -U gateway -d mcp_gateway \
  -c 'select tool_name, result_category from tool_invocation_audit order by id desc;'
```

The local Postgres connection is:

```text
database: mcp_gateway
username: gateway
password: gateway
```

These credentials and the unauthenticated local identity are for local
development only.

## Stop or reset the stack

Stop containers while retaining the database volume:

```bash
docker compose down
```

Remove containers and the database volume:

```bash
docker compose down -v
```

The second command deletes local audit rows and sample database state. Use it
only when a clean reset is intended. In-memory quota counters reset when the
gateway process restarts.
