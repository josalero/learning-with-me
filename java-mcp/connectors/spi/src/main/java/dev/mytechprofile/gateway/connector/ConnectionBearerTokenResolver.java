package dev.mytechprofile.gateway.connector;

/**
 * Optional resolver for outbound bearer tokens attached by OpenAPI connectors.
 */
@FunctionalInterface
public interface ConnectionBearerTokenResolver {

    /**
     * @param connectionName catalog connection name
     * @return token without {@code Bearer } prefix, or {@code null} when none
     */
    String resolve(String connectionName);
}
