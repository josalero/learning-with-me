# Documentation

Use this page to choose the shortest path for your task. All commands assume
the repository root unless a document says otherwise.

## Choose a path

| Audience or goal | Recommended path |
|---|---|
| Run the gateway for the first time | [How to use](HOW-TO-USE.md) → [MCP client integration](../integrations/mcp-client/README.md) |
| Build an MCP client | [MCP client guide](guides/build-mcp-client.md) → [MCP client integration](../integrations/mcp-client/README.md) |
| Integrate an application | [Integration patterns](guides/integration-patterns.md) → [REST facade integration](../integrations/rest-facade/README.md) |
| Build a connector | [Connector guide](guides/build-connector.md) → [Echo connector sample](../connectors/samples/echo/README.md) |
| Configure or operate a catalog | [Catalog reference](reference/catalog.md) → [External catalog sample](../samples/catalogs/external/README.md) |
| Understand the design | [Architecture](architecture.md) → [Transport ADR](adr/001-mcp-transport.md) |
| Evaluate production readiness | [Known limitations](known-limitations.md) |
| Demonstrate or validate the repository | [Demo runbook](demo-runbook.md) → [QA skill](../.agents/skills/mcp-gateway-qa-agent/SKILL.md) |

## Documentation set

| Document | Purpose |
|---|---|
| [How to use](HOW-TO-USE.md) | Primary operational guide: start, connect, configure, secure, and troubleshoot |
| [Connectors](../connectors/README.md) | Reusable connector libraries and connector samples |
| [Integrations](../integrations/README.md) | Runnable applications that consume the gateway |
| [Samples](../samples/README.md) | Downstream fixtures and deployment configuration examples |
| [Architecture](architecture.md) | Components, flows, trust boundaries, and extension model |
| [Catalog reference](reference/catalog.md) | Supported YAML fields, constraints, and defaults |
| [Connector guide](guides/build-connector.md) | Build, test, package, and install a connector library |
| [MCP client guide](guides/build-mcp-client.md) | Configure, code, secure, and test an application that calls gateway tools |
| [Integration patterns](guides/integration-patterns.md) | Choose an application-to-gateway integration style |
| [Docker Compose](../docker/README.md) | Services, profiles, overrides, state, and lifecycle |
| [Known limitations](known-limitations.md) | Current constraints, impact, and production action |
| [ADR 001](adr/001-mcp-transport.md) | Stateful Streamable HTTP transport decision |

## Maintenance conventions

- Keep only current behavior in these documents; do not add implementation
  plans or iteration histories.
- Put end-to-end operations in `HOW-TO-USE.md` and module-specific commands in
  the relevant connector, integration, or sample README.
- Treat [the catalog model](../gateway/src/main/java/dev/mytechprofile/gateway/config/GatewayProperties.java)
  and checked-in catalog YAML as the configuration source of truth.
- Do not edit applied Flyway migrations. Add a new migration instead.
- Update documentation and runnable examples in the same change when commands,
  profiles, endpoints, or catalog fields change.
