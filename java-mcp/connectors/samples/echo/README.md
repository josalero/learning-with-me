# Echo connector example

This module is a minimal third-party-style connector library. Its package is
outside the gateway namespace to prove that connector installation does not
depend on component scanning.

It demonstrates:

- implementing the public `Connector` SPI;
- reading connector-specific values from `connection.settings`;
- declaring a read operation and JSON input schema;
- Spring Boot auto-configuration through JAR metadata;
- compilation by the gateway without gateway-specific connector code.

The Echo connector is intentionally not installed in the default gateway
runtime or catalog. It is loaded by integration tests as if it were an external
dependency.

## Key files

- [`EchoConnector.java`](src/main/java/com/example/mcpgateway/echo/EchoConnector.java)
- [`EchoConnectorAutoConfiguration.java`](src/main/java/com/example/mcpgateway/echo/EchoConnectorAutoConfiguration.java)
- [`AutoConfiguration.imports`](src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)

## Catalog shape

```yaml
gateway:
  connections:
    echo-service:
      type: echo
      settings:
        prefix: "governed: "
  tools:
    echo_message:
      owner: examples
      version: "1"
      connection: echo-service
      operation: echo
      mode: read
      description: Echo a governed message.
      input:
        include: [message]
      output:
        include: [message]
```

## Verify

Run from the repository root:

```bash
./gradlew :example-connector-echo:build
./gradlew :integration-tests:test --tests '*ExternalConnector*'
```

To install an equivalent connector, publish its JAR, add it to the gateway as a
`runtimeOnly` dependency, and provide matching connection/tool catalog entries.
For publication and production connector requirements, see
[Build a connector library](../../../docs/guides/build-connector.md).
