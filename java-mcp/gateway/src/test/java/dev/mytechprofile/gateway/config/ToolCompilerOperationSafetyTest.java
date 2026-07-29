package dev.mytechprofile.gateway.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationAccess;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.pipeline.ContextExpressionParser;

class ToolCompilerOperationSafetyTest {

    @Test
    void refusesToPublishConnectorClassifiedWriteAsRead() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");

        Connector connector = new Connector() {
            @Override
            public String type() {
                return "example";
            }

            @Override
            public ConnectorCapabilities capabilities() {
                return ConnectorCapabilities.discoverable();
            }

            @Override
            public List<OperationDescriptor> discover(ConnectionDefinition connection) {
                return List.of(new OperationDescriptor(
                        "mutate",
                        "Mutate",
                        schema,
                        null,
                        OperationAccess.WRITE));
            }

            @Override
            public ExecutionResult execute(
                    OperationInvocation invocation, ToolExecutionContext context) {
                throw new UnsupportedOperationException();
            }
        };

        ToolCompiler compiler = new ToolCompiler(
                List.of(connector),
                new SchemaProjector(mapper),
                new ContextExpressionParser(),
                mapper);
        GatewayProperties properties = new GatewayProperties(
                1,
                null,
                null,
                Map.of(
                        "example",
                        new GatewayProperties.ConnectionProperties(
                                "example", null, null, null, null, null, null, Map.of())),
                Map.of(
                        "unsafe_mutation",
                        new GatewayProperties.ToolProperties(
                                "example",
                                "mutate",
                                "read",
                                "Unsafe",
                                null,
                                Map.of(),
                                null,
                                null,
                                null,
                                false,
                                "test",
                                "1",
                                false)));

        assertThatThrownBy(() -> compiler.compile(properties))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("mutating operation")
                .hasMessageContaining("mode: write");
    }
}
