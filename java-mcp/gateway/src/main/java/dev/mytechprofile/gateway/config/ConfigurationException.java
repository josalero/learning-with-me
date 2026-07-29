package dev.mytechprofile.gateway.config;

/**
 * Fail-fast configuration error that aborts gateway startup.
 *
 * <p><strong>When to use:</strong> thrown by {@link ToolCompiler} and
 * {@link SchemaProjector} when YAML or OpenAPI projection is invalid.
 */
public final class ConfigurationException extends RuntimeException {

    /**
     * Creates a configuration failure.
     *
     * @param message actionable diagnostic (tool/connection name when known)
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a configuration failure with cause.
     *
     * @param message actionable diagnostic
     * @param cause underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
