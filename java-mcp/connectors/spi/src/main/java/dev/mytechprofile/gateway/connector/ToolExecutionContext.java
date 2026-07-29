package dev.mytechprofile.gateway.connector;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-invocation context shared across the governance pipeline and connectors.
 *
 * <p><strong>When to use:</strong> created at the start of each tool call. Connectors
 * may read identity and correlation ids for outbound headers or audit; they must
 * not mutate identity.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * ToolExecutionContext context = ToolExecutionContext.start(identityResolver.resolve());
 * connector.execute(invocation, context);
 * }</pre>
 *
 * @param invocationId unique id for this tool call
 * @param correlationId id propagated to traces and downstream systems
 * @param identity authenticated or synthetic caller
 * @param startedAt invocation start timestamp
 */
public record ToolExecutionContext(
        String invocationId,
        String correlationId,
        CallerIdentity identity,
        Instant startedAt) {

    /**
     * Starts a new context with matching invocation and correlation ids.
     *
     * @param identity resolved caller; must not be {@code null}
     * @return fresh context
     */
    public static ToolExecutionContext start(CallerIdentity identity) {
        String id = UUID.randomUUID().toString();
        return new ToolExecutionContext(id, id, identity, Instant.now());
    }
}
