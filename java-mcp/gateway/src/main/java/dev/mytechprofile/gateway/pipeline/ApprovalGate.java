package dev.mytechprofile.gateway.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;

/**
 * Requires human approval via MCP elicitation for write tools.
 */
@Component
public class ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(ApprovalGate.class);

    private static final Map<String, Object> APPROVAL_SCHEMA = Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "properties",
            Map.of("approved", Map.of("type", "boolean")),
            "required",
            List.of("approved"));

    /**
     * Ensures write tools obtain human acceptance before connector execution.
     *
     * @return approval outcome for audit ({@code NOT_REQUIRED}, {@code ACCEPTED})
     */
    public String require(
            CompiledTool tool,
            Map<String, Object> arguments,
            ToolExecutionContext context,
            ToolContext toolContext) {
        if (!tool.requiresApproval()) {
            return "NOT_REQUIRED";
        }

        Optional<McpSyncServerExchange> exchange = McpToolUtils.getMcpExchange(toolContext);
        if (exchange.isEmpty()) {
            throw new GatewayException(
                    new GatewayError.AccessDenied("approval required but no MCP exchange is available"));
        }

        ElicitRequest request = ElicitRequest.builder()
                .message(describe(tool, arguments))
                .requestedSchema(APPROVAL_SCHEMA)
                .build();

        ElicitResult result;
        try {
            result = exchange.get().createElicitation(request);
        } catch (UnsupportedOperationException ex) {
            log.warn("client cannot elicit approval for tool='{}'", tool.name());
            throw new GatewayException(new GatewayError.AccessDenied(
                    "approval required but client cannot elicit confirmation"));
        } catch (RuntimeException ex) {
            log.warn("elicitation failed for tool='{}': {}", tool.name(), ex.toString());
            throw new GatewayException(new GatewayError.AccessDenied(
                    "approval required but client cannot elicit confirmation"));
        }

        if (result == null || result.action() != ElicitResult.Action.ACCEPT) {
            throw new GatewayException(new GatewayError.AccessDenied("approval declined by user"));
        }
        return "ACCEPTED";
    }

    private static String describe(CompiledTool tool, Map<String, Object> arguments) {
        return "Approve %s for subject-scoped call (%d argument keys)?"
                .formatted(tool.name(), arguments == null ? 0 : arguments.size());
    }
}
