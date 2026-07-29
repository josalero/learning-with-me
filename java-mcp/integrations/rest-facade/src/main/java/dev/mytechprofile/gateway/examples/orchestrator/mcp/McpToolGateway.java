package dev.mytechprofile.gateway.examples.orchestrator.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * Thin MCP tool port used by domain services in place of Feign/RestClient.
 */
@Service
public class McpToolGateway {

    private final ToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;
    private final ApprovalElicitationHandler elicitationHandler;

    public McpToolGateway(
            ToolCallbackProvider toolCallbackProvider,
            ObjectMapper objectMapper,
            ApprovalElicitationHandler elicitationHandler) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.objectMapper = objectMapper;
        this.elicitationHandler = elicitationHandler;
    }

    public List<ToolSummary> listTools() {
        return Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(callback -> {
                    var def = callback.getToolDefinition();
                    return new ToolSummary(def.name(), def.description(), def.inputSchema());
                })
                .toList();
    }

    /**
     * Calls an MCP tool and returns the gateway JSON payload (unwraps MCP text content when needed).
     *
     * @param toolName catalog tool name
     * @param arguments tool arguments (serialized to JSON)
     * @param approveWrites when the gateway elicits approval, accept ({@code true}) or decline
     */
    public JsonNode callTool(String toolName, Map<String, Object> arguments, boolean approveWrites) {
        ToolCallback callback = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(c -> toolName.equals(c.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown MCP tool: " + toolName));

        elicitationHandler.setApprove(approveWrites);
        try {
            String raw = callback.call(objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments));
            return parseToolResult(raw);
        } finally {
            elicitationHandler.clear();
        }
    }

    private JsonNode parseToolResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.readTree(raw);
        if (node.isArray() && !node.isEmpty()) {
            JsonNode first = node.get(0);
            if (first.has("text") && first.get("text").isString()) {
                return objectMapper.readTree(first.get("text").asString());
            }
        }
        if (node.isObject() && node.has("text") && node.get("text").isString()) {
            return objectMapper.readTree(node.get("text").asString());
        }
        return node;
    }

    public record ToolSummary(String name, String description, String inputSchema) {}

    @Component
    public static class ApprovalElicitationHandler {

        private final ThreadLocal<Boolean> approve = ThreadLocal.withInitial(() -> Boolean.TRUE);

        void setApprove(boolean value) {
            approve.set(value);
        }

        void clear() {
            approve.remove();
        }

        @McpElicitation(clients = "gateway")
        ElicitResult handle(ElicitRequest request) {
            if (Boolean.TRUE.equals(approve.get())) {
                return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("approved", true));
            }
            return new ElicitResult(ElicitResult.Action.DECLINE, Map.of());
        }
    }
}
