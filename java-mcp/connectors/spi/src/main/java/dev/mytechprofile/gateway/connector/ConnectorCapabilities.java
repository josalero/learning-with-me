package dev.mytechprofile.gateway.connector;

/**
 * Capability flags for a {@link Connector} implementation.
 *
 * <p><strong>When to use:</strong> return from {@link Connector#capabilities()} so
 * the configuration compiler knows whether to expect a specification file or
 * inline declared operations.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * // OpenAPI — operations come from the specification
 * ConnectorCapabilities.discoverable();
 *
 * // SQL — operations are declared under connections.*.operations
 * ConnectorCapabilities.declaredOnly();
 * }</pre>
 *
 * @param supportsDiscovery {@code true} when {@link Connector#discover} reads a contract
 */
public record ConnectorCapabilities(boolean supportsDiscovery) {

    /**
     * Connector that can list operations from an external contract (OpenAPI, …).
     *
     * @return discoverable capabilities
     */
    public static ConnectorCapabilities discoverable() {
        return new ConnectorCapabilities(true);
    }

    /**
     * Connector that only exposes operations declared in configuration.
     *
     * @return declared-only capabilities
     */
    public static ConnectorCapabilities declaredOnly() {
        return new ConnectorCapabilities(false);
    }
}
