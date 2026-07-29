package dev.mytechprofile.gateway.examples.orchestrator.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import tools.jackson.databind.json.JsonMapper;

class McpToolGatewayTest {

    private ToolCallbackProvider provider;
    private McpToolGateway gateway;

    @BeforeEach
    void setUp() {
        provider = mock(ToolCallbackProvider.class);
        gateway = new McpToolGateway(
                provider, JsonMapper.builder().build(), new McpToolGateway.ApprovalElicitationHandler());
    }

    @Test
    void callTool_unwrapsMcpTextContent() {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition())
                .thenReturn(ToolDefinition.builder()
                        .name("secure_ping")
                        .description("ping")
                        .inputSchema("{}")
                        .build());
        when(callback.call("{}"))
                .thenReturn("[{\"text\":\"{\\\"ok\\\":true,\\\"data\\\":{\\\"requestId\\\":\\\"r1\\\"}}\"}]");
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[] {callback});

        var result = gateway.callTool("secure_ping", Map.of(), true);

        assertThat(result.path("ok").asBoolean()).isTrue();
        assertThat(result.path("data").path("requestId").asString()).isEqualTo("r1");
    }

    @Test
    void callTool_unknownName_throws() {
        when(provider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

        assertThatThrownBy(() -> gateway.callTool("missing", Map.of(), true))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing");
    }
}
