package dev.mytechprofile.gateway.connector;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Immutable connection metadata compiled from gateway configuration.
 *
 * <p><strong>When to use:</strong> pass to {@link Connector#discover} and embed in
 * {@link OperationInvocation} during execution. Do not put secrets in this record;
 * authentication material stays in Spring Security client registrations.
 *
 * <p><strong>Example YAML mapping:</strong>
 * <pre>{@code
 * connections:
 *   recruiting-api:
 *     type: openapi
 *     specification: classpath:/openapi/recruiting.yaml
 *     base-url: http://127.0.0.1:9080
 * }</pre>
 *
 * @param name unique connection name referenced by tools
 * @param type connector type matching {@link Connector#type()}
 * @param baseUri downstream base URI for REST connectors; may be unused for SQL
 * @param specificationLocation classpath or URL of the OpenAPI document; {@code null} for SQL
 * @param connectTimeout TCP connect timeout
 * @param readTimeout response read timeout
 * @param attributes non-secret connector metadata compiled from catalog configuration
 */
public record ConnectionDefinition(
        String name,
        String type,
        URI baseUri,
        String specificationLocation,
        Duration connectTimeout,
        Duration readTimeout,
        Map<String, String> attributes) {

    public ConnectionDefinition {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Backward-compatible constructor for connections without connector attributes. */
    public ConnectionDefinition(
            String name,
            String type,
            URI baseUri,
            String specificationLocation,
            Duration connectTimeout,
            Duration readTimeout) {
        this(name, type, baseUri, specificationLocation, connectTimeout, readTimeout, Map.of());
    }
}
