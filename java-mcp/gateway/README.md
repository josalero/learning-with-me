# Gateway service

This module is the deployable governed MCP server. It compiles explicitly
configured connector operations into MCP tools and applies validation,
authorization, quotas, trusted context, approvals, output policy, and audit.

The module depends on `:connector-spi` at compile time. A gateway distribution
selects concrete connectors through `runtimeOnly` dependencies.

## Run locally

Start Postgres and the sample downstream services:

```bash
docker compose up -d --wait postgres recruiting-api protected-api
```

Run the gateway with its synthetic local identity:

```bash
./gradlew :gateway:bootRun --args='--spring.profiles.active=local'
```

The MCP endpoint is `http://127.0.0.1:8080/mcp`. The local profile is
unauthenticated and must not be exposed outside a developer machine.

## Key locations

| Path | Purpose |
|---|---|
| `src/main/java/.../config` | Catalog binding and compilation |
| `src/main/java/.../pipeline` | Connector-neutral governance |
| `src/main/java/.../transport` | MCP and operational endpoints |
| `src/main/java/.../security` | Inbound identity and outbound credentials |
| `src/main/java/.../audit` | Invocation audit metadata |
| `src/main/resources/gateway/catalog` | Bundled connection and tool packs |
| `src/main/resources/openapi` | Bundled OpenAPI specifications |

Use [How to use the gateway](../docs/HOW-TO-USE.md) for the complete runnable
workflow, [Architecture](../docs/architecture.md) for runtime boundaries, and
the [catalog reference](../docs/reference/catalog.md) for configuration.
