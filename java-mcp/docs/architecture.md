# Architecture

The MCP Integration Gateway publishes explicitly selected backend operations as
MCP tools and applies the same governance pipeline regardless of connector
type.

## Terminology

| Term | Meaning |
|---|---|
| Connector | Library that configures a backend type, describes operations, and executes calls |
| Connection | Named backend configuration used by one or more tools |
| Operation | Connector-owned backend action, such as an OpenAPI `operationId` or named SQL query |
| Tool | Model-visible, governed projection of one connection operation |
| Catalog | YAML maps of connections and explicitly published tools |
| Trusted context | Caller or request data injected by the gateway, never accepted from model arguments |
| Output projection | Allowlisting, masking, and array limiting applied before data leaves the gateway |

## System context

```text
                deployment configuration
                         │
                         ▼
                    catalog YAML
                         │
                         ▼
MCP client ──► MCP transport ──► compiled tool catalog
                                        │
                                        ▼
                  validate → authorize → quota
                       → trusted context → approval
                                        │
                                        ▼
                                  connector SPI
                                   │         │
                                   ▼         ▼
                               REST API   SQL database
                                        │
                                        ▼
                           output projection → audit
```

## Module boundaries

| Boundary | Responsibility |
|---|---|
| `connector-spi` | Stable connection, operation, execution, result, and caller-identity contracts |
| Connector JAR | Connector-specific configuration validation, discovery plans, and backend execution |
| `gateway.config` | Bind YAML and compile connector descriptors plus governance policy into tools |
| `gateway.pipeline` | Apply connector-neutral invocation governance |
| `gateway.transport` | Adapt compiled tools to Spring AI MCP callbacks |
| Gateway application | Bundle connector JARs and operational adapters such as security, audit, and metrics |

Connectors do not import MCP types. The gateway does not require connector
packages to live under its component-scan namespace.

## Source organization

Physical folders describe ownership and deployment role; logical Gradle project
names remain stable:

| Folder | Contents |
|---|---|
| [`connectors`](../connectors/README.md) | Publishable SPI, test kit, bundled connector JARs, and connector samples |
| [`gateway`](../gateway/README.md) | The deployable governed MCP service |
| [`integrations`](../integrations/README.md) | Applications that consume gateway tools |
| [`samples`](../samples/README.md) | Downstream fixtures and deployment configuration used by demonstrations |
| [`tests`](../tests/README.md) | Cross-module architecture and integration validation |

This separation prevents sample backends from appearing to be connector
libraries and prevents MCP clients from appearing to be gateway internals.

## Startup flow

1. Spring loads the base configuration and imported catalog files.
2. Connector JARs register through Spring Boot auto-configuration.
3. The compiler validates each connection and calls
   `Connector.configure(...)`.
4. Each connector describes available operations through
   `Connector.discover(...)`.
5. The compiler resolves explicitly configured tools, projects their schemas,
   and validates governance rules.
6. The immutable compiled catalog is adapted to MCP tool callbacks.
7. Startup fails if a connector, connection, operation, schema projection, or
   safety rule is invalid.

Discovery does not publish operations automatically. Only `gateway.tools`
entries become MCP tools.

## Invocation flow

For every tool call, the gateway:

1. Resolves the caller identity at the MCP transport boundary.
2. Validates model arguments against the published schema.
3. Enforces required roles and scopes.
4. Applies the per-subject quota.
5. Adds trusted context such as tenant, subject, and correlation ID.
6. Requires elicitation approval for writes.
7. Executes the connector operation.
8. Converts expected failures to stable gateway errors.
9. Projects and masks successful output.
10. Emits audit metadata and invocation metrics.

Unexpected connector exceptions are normalized and audited without exposing
internal exception details to the caller.

## Connector execution

### OpenAPI

During discovery, the OpenAPI connector builds private HTTP execution plans
from each `operationId`: method, path, parameter locations, JSON request body,
schema, and read/write classification. At invocation, it maps validated
arguments to that plan and returns the actual downstream status.

Mutating HTTP operations must be published as `mode: write`; every write tool
must require approval.

### SQL

SQL operations are declared by name in the connection configuration. The
connector accepts one parameterized `SELECT` statement, requires a positive row
limit, applies a JDBC query timeout, and resolves a named `DataSource`.
Database permissions remain the primary mutation boundary.

### Third-party connectors

A gateway distribution installs a connector by adding its JAR as a runtime
dependency:

```gradle
runtimeOnly "com.example:gateway-connector-example:1.0.0"
```

The connector owns values under `connections.<name>.settings`; the gateway core
does not add connector-specific properties. See the
[connector guide](guides/build-connector.md).

## Trust boundaries

- Model-provided arguments are untrusted and schema validated.
- Caller identity is trusted only when resolved by the configured transport
  security adapter.
- Catalog files are deployment-controlled input and are compiled before the
  server accepts traffic.
- Secrets belong in environment-backed or secret-manager-backed resolvers, not
  compiled connection attributes.
- Downstream responses are untrusted until output projection completes.
- Audit records contain metadata and a salted argument hash, not argument or
  response bodies.

The default local profile deliberately relaxes inbound authentication and must
not be exposed outside a developer machine.

## Catalog lifecycle and operations

Bundled catalogs are imported from the classpath. Deployment-managed catalogs
can be mounted with
[`docker-compose.catalog.yml`](../docker-compose.catalog.yml) without rebuilding
the image.

Catalogs are immutable for the lifetime of a gateway process. Validate and
restart the gateway to apply a new catalog version. The sanitized compiled
catalog is available at `/actuator/gatewaycatalog`.

See [ADR 001](adr/001-mcp-transport.md) for the stateful transport decision and
[Known limitations](known-limitations.md) for production boundaries.
