package dev.mytechprofile.gateway.transport;

import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.ToolResult;
import dev.mytechprofile.gateway.pipeline.ToolInvocationPipeline;

/**
 * Spring AI {@link ToolCallback} backed by a {@link CompiledTool}.
 *
 * <p><strong>When to use:</strong> created by {@link McpTransportConfiguration} for
 * each catalog entry. Supplies a dynamic JSON Schema (not annotation-derived) and
 * delegates execution to {@link ToolInvocationPipeline}.
 *
 * <p><strong>Check B:</strong> logs whether {@link McpToolUtils#getMcpExchange}
 * is present so approval elicitation works from dynamic callbacks.
 *
 * <p><strong>Example schema publication:</strong>
 * <pre>{@code
 * ToolDefinition def = callback.getToolDefinition();
 * // def.name() == "search_candidates"
 * // def.inputSchema() == compiled JSON Schema string
 * }</pre>
 */
public final class CatalogToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(CatalogToolCallback.class);

    private final CompiledTool tool;
    private final ToolInvocationPipeline pipeline;
    private final ObjectMapper mapper;

    /**
     * Creates a callback for one compiled tool.
     *
     * @param tool compiled catalog entry
     * @param pipeline governance pipeline
     * @param mapper JSON mapper
     */
    public CatalogToolCallback(
            CompiledTool tool, ToolInvocationPipeline pipeline, ObjectMapper mapper) {
        this.tool = tool;
        this.pipeline = pipeline;
        this.mapper = mapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DefaultToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema().toString())
                .build();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    /**
     * Invokes the pipeline and returns a JSON result string for the MCP client.
     *
     * @param toolInput JSON object of arguments; blank becomes {@code {}}
     * @param toolContext may carry the MCP server exchange
     * @return JSON {@link ToolResult#asMap()} serialization
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        boolean exchangePresent = McpToolUtils.getMcpExchange(toolContext).isPresent();
        log.info(
                "CHECK B — tool='{}' toolContextPresent={} mcpExchangePresent={}",
                tool.name(),
                toolContext != null,
                exchangePresent);

        try {
            Map<String, Object> arguments = mapper.readValue(
                    toolInput == null || toolInput.isBlank() ? "{}" : toolInput,
                    new TypeReference<>() {});
            ToolResult result = pipeline.invoke(tool, arguments, toolContext);
            return mapper.writeValueAsString(result.asMap());
        } catch (JacksonException ex) {
            return """
                    {"ok":false,"error":"invalid_arguments","message":"request body was not valid JSON"}""";
        }
    }
}
