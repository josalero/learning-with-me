package dev.mytechprofile.gateway.connector;

import tools.jackson.databind.JsonNode;

/**
 * Discovered or declared operation available on a connection.
 *
 * <p><strong>When to use:</strong> produced by {@link Connector#discover} and consumed
 * by the tool compiler to build MCP input schemas. Discovery alone never publishes
 * an MCP tool — publication requires an explicit tools entry.
 *
 * <p><strong>Example:</strong> OpenAPI {@code operationId: searchCandidates} becomes
 * a descriptor whose {@code inputSchema} is later narrowed by {@code input.include}.
 *
 * @param operationId stable id matching configuration {@code tools.*.operation}
 * @param summary human-readable summary for operators and default tool descriptions
 * @param inputSchema JSON Schema for model-visible (pre-projection) inputs
 * @param outputSchema JSON Schema for response shaping when known; may be {@code null}
 * @param access connector-derived side-effect classification
 */
public record OperationDescriptor(
        String operationId,
        String summary,
        JsonNode inputSchema,
        JsonNode outputSchema,
        OperationAccess access) {

    /**
     * Backward-compatible constructor for connectors that cannot yet classify operations.
     */
    public OperationDescriptor(
            String operationId, String summary, JsonNode inputSchema, JsonNode outputSchema) {
        this(operationId, summary, inputSchema, outputSchema, OperationAccess.UNKNOWN);
    }
}
