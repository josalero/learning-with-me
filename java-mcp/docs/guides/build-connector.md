# Build a connector library

Use a connector library when the gateway must talk to a backend that the
bundled OpenAPI and SQL connectors cannot represent. A connector owns backend
configuration, operation discovery, and execution. It must not depend on
Spring AI or MCP protocol types.

For a working implementation, see
[`connectors/samples/echo`](../../connectors/samples/echo/README.md).

## 1. Add the connector dependencies

```gradle
dependencies {
    api "dev.mytechprofile.gateway:connector-spi:<version>"
    implementation "org.springframework.boot:spring-boot-autoconfigure"

    testImplementation "dev.mytechprofile.gateway:connector-testkit:<version>"
}
```

The connector SPI is the public extension boundary. Depending on gateway
runtime modules couples the connector to implementation details and is not
supported.

## 2. Implement `Connector`

```java
public final class ExampleConnector implements Connector {
    @Override
    public String type() {
        return "example";
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return ConnectorCapabilities.declaredOnly();
    }

    @Override
    public ConnectionDefinition configure(
            ConnectionDefinition common, JsonNode configuration) {
        JsonNode settings = configuration.path("settings");
        // Validate connector settings.
        // Return only safe, non-secret compiled attributes.
        return common;
    }

    @Override
    public List<OperationDescriptor> discover(
            ConnectionDefinition connection) {
        // Return operation schemas and READ or WRITE classifications.
    }

    @Override
    public ExecutionResult execute(
            OperationInvocation invocation,
            ToolExecutionContext context) {
        // Translate expected backend failures to ExecutionResult.Failure.
    }
}
```

Keep the connector type stable after release. Catalog connections use this
value to select the connector.

## 3. Register Spring Boot auto-configuration

Provide an `@AutoConfiguration` class with a conditional connector bean:

```java
@AutoConfiguration
public class ExampleConnectorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ExampleConnector.class)
    ExampleConnector exampleConnector() {
        return new ExampleConnector();
    }
}
```

List the class in:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

The gateway does not scan third-party package names. Auto-configuration makes
the connector JAR self-registering when it is on the runtime classpath.

## 4. Test the connector contract

Use the shared test kit for the baseline discovery contract:

```java
ConnectorContractAssertions.assertDiscoveryContract(connector, connection);
```

Add connector-specific tests for:

- valid and invalid configuration;
- operation discovery and JSON schemas;
- successful read and write execution;
- timeout, authentication, authorization, and backend failures;
- secret and unsafe-body redaction;
- Spring Boot auto-configuration.

The gateway does not publish every discovered operation. It publishes only the
operations explicitly mapped to tools in the catalog.

## 5. Publish the library

For local development:

```bash
./gradlew publishToMavenLocal
```

For a shared environment, publish the connector to the same Maven repository
used by the gateway distribution. Version the SPI compatibility and connector
behavior deliberately; changing a connector type or operation identifier can
invalidate existing catalogs.

## 6. Install the connector in a gateway distribution

Add the connector as a runtime dependency:

```gradle
dependencies {
    runtimeOnly "com.example:connector-example:<version>"
}
```

Then add a matching connection and tool mapping to the gateway catalog:

```yaml
gateway:
  connections:
    example-service:
      type: example
      settings:
        endpoint: https://example.internal

  tools:
    example_lookup:
      connection: example-service
      operation: lookup
      mode: READ
      authorization:
        scopes: [example.read]
```

Restart the gateway and confirm the tool appears in
`/actuator/gatewaycatalog` and MCP discovery.

## Release checklist

- `type()` is unique and stable.
- Configuration fails at startup when required settings are invalid.
- `OperationAccess` correctly identifies read and write side effects.
- Secrets remain in a secret or token resolver, not
  `ConnectionDefinition`.
- Expected failures return stable `ExecutionResult.Failure` values.
- Exceptions, credentials, and unsafe downstream bodies never reach callers.
- Connector and auto-configuration tests pass.
- The gateway distribution declares the connector as `runtimeOnly`.
- Catalog and example documentation are updated with the supported operations.
