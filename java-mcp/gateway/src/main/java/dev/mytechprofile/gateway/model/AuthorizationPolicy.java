package dev.mytechprofile.gateway.model;

import java.util.List;

import dev.mytechprofile.gateway.config.GatewayProperties.AuthorizationProperties;

/**
 * Authorization requirements enforced for every invocation.
 *
 * @param requiredScopes OAuth / tool scopes
 * @param requiredRoles application roles
 */
public record AuthorizationPolicy(List<String> requiredScopes, List<String> requiredRoles) {

    public static AuthorizationPolicy none() {
        return new AuthorizationPolicy(List.of(), List.of());
    }

    public static AuthorizationPolicy from(AuthorizationProperties properties) {
        if (properties == null) {
            return none();
        }
        return new AuthorizationPolicy(
                properties.requiredScopes() == null ? List.of() : List.copyOf(properties.requiredScopes()),
                properties.requiredRoles() == null ? List.of() : List.copyOf(properties.requiredRoles()));
    }
}
