package dev.mytechprofile.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import com.example.mcpgateway.echo.EchoConnector;

import dev.mytechprofile.gateway.config.GatewayProperties;
import dev.mytechprofile.gateway.config.SchemaProjector;
import dev.mytechprofile.gateway.config.ToolCompiler;
import dev.mytechprofile.gateway.pipeline.ContextExpressionParser;

class ExternalConnectorCompilationTest {

    @Test
    void compilesThirdPartyConnectorSettingsWithoutGatewaySpecificCode() {
        ObjectMapper mapper = new ObjectMapper();
        ToolCompiler compiler = new ToolCompiler(
                java.util.List.of(new EchoConnector()),
                new SchemaProjector(mapper),
                new ContextExpressionParser(),
                mapper);
        GatewayProperties properties = new GatewayProperties(
                1,
                null,
                null,
                Map.of(
                        "echo-service",
                        new GatewayProperties.ConnectionProperties(
                                "echo",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of("prefix", "governed: "))),
                Map.of(
                        "echo_message",
                        new GatewayProperties.ToolProperties(
                                "echo-service",
                                "echo",
                                "read",
                                "Echo",
                                null,
                                Map.of(),
                                null,
                                null,
                                null,
                                false,
                                "examples",
                                "1",
                                false)));

        var tools = compiler.compile(properties);
        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("echo_message");
            assertThat(tool.connection().attributes())
                    .containsEntry("prefix", "governed: ");
        });
    }
}
