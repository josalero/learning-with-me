# ADR 001 — Use stateful Streamable HTTP MCP

**Status:** Accepted  
**Date:** 2026-07-29

## Context

The gateway publishes catalog-compiled tools as Spring AI `ToolCallback`
instances. Approval-gated write tools use MCP elicitation, so a callback must
have access to the active MCP exchange.

## Decision drivers

- Support remote clients over HTTP.
- Publish tools compiled dynamically from the catalog.
- Preserve the active MCP exchange during tool execution.
- Support approval elicitation for governed writes.
- Keep MCP framework types out of connector libraries.
- Verify the transport through a packaged, runnable client and server.

## Decision

Use a synchronous, stateful Streamable HTTP MCP server:

| Choice | Value |
|---|---|
| Spring Boot | 4.1.0 |
| Spring AI | 2.0.0 |
| Server starter | `spring-ai-starter-mcp-server-webmvc` |
| Server type | `SYNC` |
| Server protocol | `STREAMABLE` |
| Endpoint | `/mcp` |
| Client starter | `spring-ai-starter-mcp-client-webflux` |
| Exchange access | `McpToolUtils.getMcpExchange(ToolContext)` |
| Jackson | Boot 4 Jackson 3 (`tools.jackson.databind.*`) |

## Alternatives considered

### Standard input and output

Standard input/output is convenient for a single local process but does not fit
the shared, remotely deployed gateway model.

### Stateless Streamable HTTP

A stateless server simplifies scaling, but approval elicitation depends on an
active client session and MCP exchange. It does not meet the current write
approval requirement.

### Statically annotated tools

Static tool methods are simple but cannot represent tools compiled from an
external catalog and connector discovery. The gateway requires dynamic
callbacks.

## Consequences

- The server maintains MCP session state for elicitation.
- Connector libraries depend only on `connector-spi`; MCP types remain in the
  gateway transport and approval boundary.
- Clients must support Streamable HTTP MCP.
- Approval-gated tools require clients with elicitation support.
- The unauthenticated local profile must not be exposed outside a developer
  machine.
- Horizontal scaling must preserve the session behavior required by the
  selected Spring AI transport.
- A future transport change must preserve dynamic discovery, tool context,
  elicitation, and the end-to-end evidence below.

## Validation

The packaged Compose stack and example MCP client verify that:

- dynamically compiled OpenAPI and SQL tools appear in MCP discovery;
- callbacks can access the active MCP exchange;
- reads execute through the governance pipeline;
- writes support accepted and declined elicitation;
- outbound bearer authentication and SQL execution use the same transport.

Run the evidence workflow from the repository root:

```bash
.agents/skills/mcp-gateway-qa-agent/scripts/verify.sh full
```
