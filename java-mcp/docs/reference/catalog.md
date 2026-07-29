# Catalog configuration reference

The catalog defines backend connections and the operations explicitly published
as MCP tools. Connector discovery never publishes an operation by itself.

Use this document when authoring bundled or external YAML. For a runnable
external-file example, see
[`samples/catalogs/external`](../../samples/catalogs/external/README.md).

## Document structure

```yaml
gateway:
  configuration-version: 1
  connections:
    connection_name:
      # Connector-specific connection configuration
  tools:
    tool_name:
      # Governed projection of one connection operation
```

`connections` and `tools` are maps keyed by stable names. Spring merges catalog
documents in configuration-precedence order, so an external file can add or
override entries. The gateway compiles the merged catalog once at startup.

## Connections

### Common fields

| Field | Required | Meaning |
|---|---:|---|
| `type` | Yes | Connector type, such as `openapi`, `sql`, or a third-party type |
| `specification` | OpenAPI | Resource location for an OpenAPI document, such as `classpath:/openapi/service.yaml` |
| `base-url` | OpenAPI | Downstream base URL |
| `datasource` | SQL deployments with multiple beans | Spring `DataSource` bean name |
| `authentication` | No | Outbound authentication configuration |
| `timeouts.connect` | No | Connection timeout; default `2s` |
| `timeouts.read` | No | Read timeout; default `5s` |
| `operations` | SQL | Map of named SQL operations |
| `settings` | Connector-specific | Values owned by a third-party connector |

Use Spring placeholders for deployment-specific values:

```yaml
base-url: ${CUSTOMER_API_BASE_URL:http://127.0.0.1:9090}
```

Do not place resolved secrets in connector attributes or committed catalog
files.

### OpenAPI authentication

| `authentication.type` | Additional fields | Behavior |
|---|---|---|
| `none` | None | No outbound bearer token |
| `static-bearer` | `token` | Adds a fixed bearer token |
| `oauth2-client-credentials` | `token-uri`, `client-id`, `client-secret` | Requests and caches an OAuth2 bearer token |

Example:

```yaml
gateway:
  connections:
    protected-api:
      type: openapi
      specification: classpath:/openapi/protected.yaml
      base-url: ${PROTECTED_API_BASE_URL:http://127.0.0.1:9081}
      authentication:
        type: oauth2-client-credentials
        token-uri: ${PROTECTED_API_TOKEN_URI:http://127.0.0.1:9081/oauth/token}
        client-id: ${PROTECTED_API_CLIENT_ID}
        client-secret: ${PROTECTED_API_CLIENT_SECRET}
      timeouts:
        connect: 2s
        read: 5s
```

### SQL operations

```yaml
gateway:
  connections:
    inventory-db:
      type: sql
      datasource: inventory
      operations:
        find_low_stock_products:
          sql: |
            select product_reference, quantity_available
              from inventory
             where warehouse_code = :warehouse
               and quantity_available <= :threshold
          parameters:
            warehouse:
              type: string
              description: Warehouse code
            threshold:
              type: integer
              minimum: 0
              maximum: 1000
          max-rows: 50
          timeout: 3s
```

| Field | Required | Meaning |
|---|---:|---|
| `sql` | Yes | One named-parameter `SELECT` statement |
| `parameters` | No | JSON Schema properties for named parameters |
| `parameters.<name>.required` | No | Defaults to `true`; set `false` to permit a missing/null value |
| `max-rows` | Yes | Positive JDBC row limit |
| `timeout` | No | Query timeout; defaults to the connection read timeout |

SQL validation rejects multiple statements, mutation keywords, and apparent
identifier interpolation. The database role remains the primary defense against
mutation.

## Tools

```yaml
gateway:
  tools:
    find_example:
      owner: example-platform
      version: "1"
      deprecated: false
      connection: example-api
      operation: findExample
      mode: read
      description: Find an example by public reference.
      input:
        include: [reference, limit]
        defaults:
          limit: 10
        override:
          limit:
            maximum: 25
      context-mappings:
        tenantId: "${identity.tenantId}"
      output:
        include: [reference, displayName]
        mask: []
        maximum-items: 25
      authorization:
        required-scopes: [tools.read]
        required-roles: [EXAMPLE_READER]
      quota:
        per-subject: 100/1h
```

### Tool fields

| Field | Required | Default or behavior |
|---|---:|---|
| Tool map key | Yes | MCP tool name; lowercase snake case starting with a letter |
| `connection` | Yes | Name from `gateway.connections` |
| `operation` | Yes | Connector operation ID |
| `mode` | Yes | `read` or `write` |
| `description` | No | Connector operation summary |
| `owner` | No | `unassigned` |
| `version` | No | `"1"` |
| `deprecated` | No | `false` |
| `requires-approval` | Writes | Must be `true` for every `mode: write` tool |

Connector-classified write operations cannot be published as reads.

### Input policy

| Field | Behavior |
|---|---|
| `input.include` | Model-visible operation fields; empty means all non-context fields |
| `input.defaults` | Values merged before validation; caller values take precedence |
| `input.override` | Per-field JSON Schema constraints |

Schema overrides may narrow an existing constraint but cannot widen it. For
example, a lower `maximum`, higher `minimum`, or smaller `enum` is valid.
Unknown fields are rejected because published schemas set
`additionalProperties: false`.

### Trusted context

`context-mappings` adds server-controlled values after model argument
validation. Supported expressions are:

| Expression | Value |
|---|---|
| `${identity.tenantId}` | Authenticated tenant |
| `${identity.subject}` | Authenticated subject |
| `${correlationId}` | Invocation correlation ID |
| `${identity.correlationId}` | Alias for the invocation correlation ID |

A context-mapped field must not appear in `input.include`.

### Output policy

| Field | Behavior |
|---|---|
| `output.include` | Recursive field allowlist; empty means passthrough |
| `output.mask` | Included fields to redact |
| `output.maximum-items` | Maximum number of items retained in arrays |

For sensitive downstreams, always configure an explicit `output.include`.
Masking recognizes email and phone-like field names; other masked values become
`***`.

### Authorization and quotas

All `authorization.required-scopes` values must be present. If
`authorization.required-roles` is non-empty, at least one listed role must be
present.

`quota.per-subject` uses `capacity/window` syntax:

```yaml
quota:
  per-subject: 100/1h
```

Supported window units are seconds (`s`), minutes (`m`), hours (`h`), and days
(`d`). Omitting the quota makes the tool unlimited.

## Startup validation

The gateway fails startup when:

- `configuration-version` is not `1`;
- a connection type has no installed connector;
- a tool references an unknown connection or operation;
- a tool name, mode, schema projection, or context expression is invalid;
- a mutating operation is exposed as a read;
- a write tool does not require approval;
- connector-specific configuration is invalid.

Validate the compiled, sanitized catalog after startup:

```bash
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog
```

## External catalogs

The checked-in override mounts an external file and sets
`SPRING_CONFIG_ADDITIONAL_LOCATION`:

```bash
docker compose -f docker-compose.yml -f docker-compose.catalog.yml \
  up -d --wait --force-recreate --no-deps gateway
curl -fsS http://127.0.0.1:8080/actuator/gatewaycatalog \
  | grep search_candidates_external
```

Restore the bundled-only catalog:

```bash
docker compose up -d --wait --force-recreate --no-deps gateway
```

External catalogs avoid an image rebuild, but changes still require a gateway
restart.
