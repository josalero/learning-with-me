# Connectors

This folder contains reusable libraries that translate backend operations into
the gateway's connector-neutral contracts. Connector libraries do not depend
on Spring AI or MCP protocol types.

| Folder | Gradle project | Purpose |
|---|---|---|
| [`spi`](spi/) | `:connector-spi` | Public connection, discovery, execution, result, and identity contracts |
| [`testkit`](testkit/) | `:connector-testkit` | Shared connector contract assertions |
| [`openapi`](openapi/) | `:connector-openapi` | Generic OpenAPI discovery and HTTP execution |
| [`sql`](sql/) | `:connector-sql` | Named, parameterized, read-only SQL execution |
| [`samples/echo`](samples/echo/README.md) | `:example-connector-echo` | Minimal independently auto-configured connector |

Build and publish the reusable connector libraries locally:

```bash
./gradlew \
  :connector-spi:publishToMavenLocal \
  :connector-testkit:publishToMavenLocal \
  :connector-openapi:publishToMavenLocal \
  :connector-sql:publishToMavenLocal
```

To implement another backend type, follow
[Build a connector library](../docs/guides/build-connector.md).
