package dev.mytechprofile.gateway.connector;

import java.util.Map;

/**
 * Normalized request handed to a {@link Connector} after governance gates pass.
 *
 * <p><strong>When to use:</strong> created by the invocation pipeline once arguments
 * are validated and trusted context (tenant, subject) has been injected.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * new OperationInvocation(
 *     connection,
 *     "searchCandidates",
 *     Map.of("skill", "Java", "tenantId", "demo-tenant", "limit", 10));
 * }</pre>
 *
 * @param connection target connection definition
 * @param operationId operation id from the connector catalog
 * @param arguments enriched argument map; never {@code null}
 */
public record OperationInvocation(
        ConnectionDefinition connection, String operationId, Map<String, Object> arguments) {}
