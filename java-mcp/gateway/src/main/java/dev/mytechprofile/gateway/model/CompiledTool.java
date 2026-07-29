package dev.mytechprofile.gateway.model;

import java.util.Map;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.pipeline.ContextExpression;

/**
 * Immutable tool ready for MCP publication and pipeline execution.
 *
 * <p>Named {@code CompiledTool} to avoid colliding with Spring AI's
 * {@code org.springframework.ai.tool.definition.ToolDefinition}.
 *
 * <p><strong>When to use:</strong> produced by {@code ToolCompiler} from YAML catalog
 * packs and held by {@link dev.mytechprofile.gateway.transport.ToolRegistry}.
 *
 * @param name MCP tool name (snake_case)
 * @param description model-visible tool description
 * @param connection downstream connection
 * @param operation connector operation id
 * @param mode read or write
 * @param inputSchema JSON Schema published to MCP clients
 * @param defaults default argument values applied before validation
 * @param contextMappings trusted field injections
 * @param outputPolicy response shaping
 * @param authorizationPolicy authorization requirements
 * @param quotaPolicy rate limits
 * @param requiresApproval whether elicitation is required before execute
 */
public record CompiledTool(
        String name,
        String description,
        ConnectionDefinition connection,
        String operation,
        ToolMode mode,
        JsonNode inputSchema,
        Map<String, Object> defaults,
        Map<String, ContextExpression> contextMappings,
        OutputPolicy outputPolicy,
        AuthorizationPolicy authorizationPolicy,
        QuotaPolicy quotaPolicy,
        boolean requiresApproval,
        ToolMetadata metadata) {

    public CompiledTool {
        metadata = metadata == null ? ToolMetadata.unspecified() : metadata;
    }

    /** Backward-compatible constructor for code-defined tools without metadata. */
    public CompiledTool(
            String name,
            String description,
            ConnectionDefinition connection,
            String operation,
            ToolMode mode,
            JsonNode inputSchema,
            Map<String, Object> defaults,
            Map<String, ContextExpression> contextMappings,
            OutputPolicy outputPolicy,
            AuthorizationPolicy authorizationPolicy,
            QuotaPolicy quotaPolicy,
            boolean requiresApproval) {
        this(
                name,
                description,
                connection,
                operation,
                mode,
                inputSchema,
                defaults,
                contextMappings,
                outputPolicy,
                authorizationPolicy,
                quotaPolicy,
                requiresApproval,
                ToolMetadata.unspecified());
    }
}
