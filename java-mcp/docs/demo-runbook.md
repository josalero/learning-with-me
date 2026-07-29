# Demo runbook — MCP Integration Gateway

Use this runbook for a repeatable demonstration of governed MCP tool calls,
restricted discovery, JWT authentication, and downstream failures. Run every
command from the repository root.

Target: under 15 minutes with a warm Docker and Gradle cache.

This page is a presenter checklist. Use
[How to use the gateway](HOW-TO-USE.md) as the maintained source for setup
explanations and troubleshooting.

## Prerequisites

- Docker Desktop or Docker Compose with `--wait` support.
- `curl`; OpenSSL is also required for the JWT checks.
- Free ports `8080`, `9080`, `9081`, and `5432`.

## Start and exercise the default stack

```bash
docker compose up --build -d --wait
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
docker compose --profile client run --rm --no-deps mcp-client
```

Expected client highlights:

- `CHECK A — discovered 4 tool(s)`
- Quota exceeded after three `search_candidates` successes.
- Approval and decline paths execute for `advance_candidate_stage`.
- `secure_ping` succeeds through outbound client credentials.
- `find_low_stock_products` returns projected inventory rows.

## Restricted identity

Always pass the restricted override **and** `--no-deps` on the client so Compose
does not recreate the gateway from the base file.

```bash
docker compose -f docker-compose.yml -f docker-compose.restricted.yml \
  up -d --wait --force-recreate --no-deps gateway
docker compose --profile client -f docker-compose.yml -f docker-compose.restricted.yml \
  run --rm --no-deps mcp-client
# expect: discovered 2 tools (no search_candidates / advance_candidate_stage)
docker compose up -d --wait --force-recreate --no-deps gateway
```

## Require inbound JWT authentication

```bash
docker compose -f docker-compose.yml -f docker-compose.jwt.yml \
  up -d --wait --force-recreate --no-deps gateway
TOKEN=$(./scripts/mint-demo-jwt.sh)
curl -sS -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8080/mcp
# Expected: 401
curl -sS -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer $(./scripts/mint-demo-jwt.sh wrong-audience)" \
  -X POST http://127.0.0.1:8080/mcp
# Expected: 401
curl -sS -o /dev/null -w '%{http_code}\n' \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -X POST http://127.0.0.1:8080/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
# Expected: 200
docker compose up -d --wait --force-recreate --no-deps gateway
```

## Inject downstream failures

```bash
RECRUITING_CHAOS_FORCE_STATUS=500 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
docker compose --profile client run --rm --no-deps mcp-client
# Expected: connector_failure

docker compose up -d --wait --force-recreate --no-deps gateway
RECRUITING_CHAOS_DELAY_MS=8000 RECRUITING_CHAOS_FORCE_STATUS=0 docker compose \
  -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
docker compose --profile client run --rm --no-deps mcp-client
# Expected: timeout

RECRUITING_CHAOS_DELAY_MS=0 RECRUITING_CHAOS_FORCE_STATUS=0 \
  docker compose -f docker-compose.yml -f docker-compose.chaos.yml \
  up -d --wait --force-recreate --no-deps recruiting-api
```

## Automated verification

```bash
.agents/skills/mcp-gateway-qa-agent/scripts/verify.sh full
```

## Continue reading

- [Docker Compose reference](../docker/README.md)
- [Known limitations](known-limitations.md)
- [MCP client integration](../integrations/mcp-client/README.md)
