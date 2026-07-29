package dev.mytechprofile.gateway.connector;

/**
 * How {@link CallerIdentity} was established for an invocation.
 *
 * <p><strong>When to use:</strong> stored on audit records so operators can tell
 * local demos from JWT-authenticated production traffic.
 */
public enum AuthenticationType {
    /** Synthetic local identity; no identity provider. */
    NONE,
    /** Inbound OAuth2 JWT resource-server validation. */
    OAUTH2_JWT
}
