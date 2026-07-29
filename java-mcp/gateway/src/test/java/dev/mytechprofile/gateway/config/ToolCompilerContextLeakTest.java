package dev.mytechprofile.gateway.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.pipeline.ContextExpressionParser;

class ToolCompilerContextLeakTest {

    @Test
    void compile_failsWhenContextMappedFieldListedInInclude() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode schemaProperties = schema.putObject("properties");
        schemaProperties.putObject("skill").put("type", "string");
        schemaProperties.putObject("tenantId").put("type", "string");
        schema.putArray("required").add("skill").add("tenantId");

        Connector connector = new Connector() {
            @Override
            public String type() {
                return "openapi";
            }

            @Override
            public ConnectorCapabilities capabilities() {
                return ConnectorCapabilities.discoverable();
            }

            @Override
            public List<OperationDescriptor> discover(ConnectionDefinition connection) {
                return List.of(new OperationDescriptor("searchCandidates", "Search", schema, null));
            }

            @Override
            public ExecutionResult execute(OperationInvocation invocation, ToolExecutionContext context) {
                throw new UnsupportedOperationException();
            }
        };

        ToolCompiler compiler = new ToolCompiler(
                List.of(connector),
                new SchemaProjector(mapper),
                new ContextExpressionParser(),
                mapper);

        GatewayProperties gatewayProperties = new GatewayProperties(
                1,
                null,
                null,
                Map.of(
                        "recruiting-api",
                        new GatewayProperties.ConnectionProperties(
                                "openapi",
                                "classpath:/openapi/recruiting.yaml",
                                "http://127.0.0.1:9080",
                                null,
                                null,
                                null,
                                null,
                                Map.of())),
                Map.of(
                        "search_candidates",
                        new GatewayProperties.ToolProperties(
                                "recruiting-api",
                                "searchCandidates",
                                "read",
                                "Search",
                                new GatewayProperties.InputProperties(
                                        List.of("skill", "tenantId"), Map.of(), Map.of()),
                                Map.of("tenantId", "${identity.tenantId}"),
                                null,
                                null,
                                null,
                                false,
                                "test",
                                "1",
                                false)));

        assertThatThrownBy(() -> compiler.compile(gatewayProperties))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("search_candidates")
                .hasMessageContaining("tenantId");
    }
}
