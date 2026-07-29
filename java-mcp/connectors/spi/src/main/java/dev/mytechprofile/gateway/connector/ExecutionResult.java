package dev.mytechprofile.gateway.connector;

import tools.jackson.databind.JsonNode;

/**
 * Normalized connector outcome. Prefer returning failures here over throwing
 * protocol-specific exceptions into the pipeline.
 *
 * <p><strong>When to use:</strong> every {@link Connector#execute} implementation
 * must return one of the sealed variants.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * return new ExecutionResult.Success(payload, 200);
 * // or
 * return new ExecutionResult.Failure(FailureKind.TIMEOUT, "searchCandidates", "read timed out");
 * }</pre>
 */
public sealed interface ExecutionResult {

    /**
     * Successful downstream call.
     *
     * @param payload JSON body (object or array)
     * @param statusCode HTTP status or synthetic {@code 200} for non-HTTP connectors
     */
    record Success(JsonNode payload, int statusCode) implements ExecutionResult {}

    /**
     * Failed downstream call with a stable failure category.
     *
     * @param kind retry/audit classification
     * @param reference operation or connection reference for operators
     * @param message safe message suitable for MCP clients (no secrets)
     */
    record Failure(FailureKind kind, String reference, String message) implements ExecutionResult {}

    /**
     * Categories used for retry policy and metrics.
     */
    enum FailureKind {
        /** Connect or read deadline exceeded. */
        TIMEOUT,
        /** Downstream rejected credentials or scope. */
        UNAUTHORIZED,
        /** Operation or resource missing. */
        NOT_FOUND,
        /** Transient downstream fault; bounded retry may be appropriate. */
        TEMPORARY,
        /** Permanent client or server error; do not retry blindly. */
        PERMANENT
    }
}
