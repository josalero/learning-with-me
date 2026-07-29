# MCP Integration Gateway

A configuration-driven Java gateway that publishes selected REST and SQL
operations as governed Model Context Protocol (MCP) tools.

The gateway centralizes input validation, caller authorization, quotas, trusted
context, write approval, output filtering, outbound authentication, and audit
metadata. Backend connectors remain independent of MCP and Spring AI types.

> **Status:** Working reference implementation. The default stack is intended
> for local development and evaluation; review
> [production limitations](docs/known-limitations.md) before deployment.

**Implementation:** Java 26 · Spring Boot 4.1.0 · Spring AI 2.0.0 · Streamable
HTTP MCP

## What it demonstrates

- Configuration-driven MCP tool publication from OpenAPI and named SQL.
- A connector SPI with independently auto-configured connector JARs.
- One governance pipeline for every connector type.
- Approval-gated writes using MCP elicitation.
- Inbound JWT and outbound OAuth2 client credentials.
- Application integration through a direct MCP client or a domain REST facade.
- External catalog injection without rebuilding the gateway image.

## Quick start

Requirements:

- Docker Desktop or Docker Compose with `--wait` support.
- Free ports `8080`, `9080`, `9081`, and `5432`.

From the repository root:

```bash
docker compose up --build -d --wait
curl -fsS http://127.0.0.1:8080/actuator/health
docker compose --profile client run --rm --no-deps mcp-client
```

The client should discover four tools and verify governed REST reads, SQL
execution, quota enforcement, accepted and declined writes, and outbound bearer
authentication.

The default `docker,local` profile uses a synthetic identity and an open `/mcp`
endpoint. Do not expose it outside a developer machine.

For local Gradle processes, security profiles, external catalogs, and
troubleshooting, follow [How to use the gateway](docs/HOW-TO-USE.md).

## Architecture at a glance

```text
catalog YAML ──► tool compiler ──► MCP tool catalog
                      │                    │
                      │                    ▼
connector SPI ◄── governance pipeline ◄── MCP clients
     │
     ▼
REST APIs / SQL databases
```

The gateway runtime depends on the public connector SPI, not concrete connector
implementations. A distribution chooses connector JARs at runtime and publishes
only operations explicitly listed in its catalog.

See [Architecture](docs/architecture.md) for component boundaries, startup and
invocation flows, and trust boundaries.

## Repository layout

| Folder | Responsibility |
|---|---|
| [`connectors`](connectors/README.md) | Reusable connector SPI, test kit, implementations, and connector samples |
| [`gateway`](gateway/README.md) | Deployable catalog compiler, governance pipeline, MCP transport, security, and audit service |
| [`integrations`](integrations/README.md) | Applications that consume governed tools through MCP |
| [`samples`](samples/README.md) | Demo downstream services and external catalog configuration |
| [`tests`](tests/README.md) | Cross-module architecture and connector integration tests |
| [`docs`](docs/README.md) | Task guides, architecture, decisions, and configuration reference |
| [`docker`](docker/README.md) | Container image and Compose operations |
| `scripts` | Local operational helpers |

Physical folders are grouped by responsibility. Existing logical Gradle project
names such as `:connector-spi`, `:example-mcp-client`, and
`:integration-tests` remain stable.

## Develop and verify

Local Gradle development requires JDK 26:

```bash
java -version
./gradlew test publishToMavenLocal
```

For the packaged end-to-end gate:

```bash
.agents/skills/mcp-gateway-qa-agent/scripts/verify.sh full
```

## Documentation index

All maintained developer-facing documents are indexed below. Start with the
[documentation guide](docs/README.md) if you are unsure which path to follow.

| Area | Document | Purpose |
|---|---|---|
| Navigation | [Documentation guide](docs/README.md) | Choose a path by task or audience |
| Getting started | [How to use the gateway](docs/HOW-TO-USE.md) | Start, connect, configure, secure, and troubleshoot |
| Architecture | [Architecture](docs/architecture.md) | Understand components, flows, trust boundaries, and extension points |
| Architecture | [ADR 001 — MCP transport](docs/adr/001-mcp-transport.md) | Understand the stateful Streamable HTTP decision |
| Configuration | [Catalog reference](docs/reference/catalog.md) | Author connections, tools, schemas, and governance policies |
| Connector development | [Build a connector](docs/guides/build-connector.md) | Implement, test, publish, and install a connector library |
| Client development | [Build an MCP client](docs/guides/build-mcp-client.md) | Discover, invoke, secure, and test gateway tools from an application |
| Application integration | [Integration patterns](docs/guides/integration-patterns.md) | Choose direct MCP, REST facade, orchestration, or automation |
| Operations | [Docker Compose reference](docker/README.md) | Manage services, profiles, overrides, state, and lifecycle |
| Demonstration | [Demo runbook](docs/demo-runbook.md) | Present the end-to-end gateway behavior |
| Production readiness | [Known limitations](docs/known-limitations.md) | Review constraints, impact, and required production actions |
| Module index | [Connectors](connectors/README.md) | Find reusable connector libraries and the connector sample |
| Module index | [Gateway service](gateway/README.md) | Run and navigate the deployable MCP service |
| Module index | [Integrations](integrations/README.md) | Find applications that consume the gateway |
| Module index | [Samples](samples/README.md) | Find downstream fixtures and catalog examples |
| Module index | [Repository tests](tests/README.md) | Run cross-module and packaged validation |
| Integration | [MCP client](integrations/mcp-client/README.md) | Discover and invoke governed tools |
| Integration | [REST facade](integrations/rest-facade/README.md) | Expose domain REST endpoints backed by MCP |
| Connector sample | [Echo connector](connectors/samples/echo/README.md) | Build an independently auto-configured connector JAR |
| Configuration sample | [External catalog](samples/catalogs/external/README.md) | Add a tool from mounted YAML without rebuilding |
| Backend sample | [Recruiting API](samples/backends/recruiting-api/README.md) | Exercise OpenAPI tools, output filtering, and failures |
| Backend sample | [Protected API](samples/backends/protected-api/README.md) | Exercise outbound OAuth2 client credentials |
