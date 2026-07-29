package dev.mytechprofile.gateway.connector;

import java.util.Set;

/**
 * Caller principal used for authorization, quotas, trusted-context injection, and audit.
 *
 * <p><strong>When to use:</strong> produced by {@code CallerIdentityResolver} from a
 * local synthetic identity or an inbound JWT. Never accept tenant or roles from
 * model-supplied tool arguments.
 *
 * <p><strong>Example — local development identity:</strong>
 * <pre>{@code
 * new CallerIdentity(
 *     "local-developer",
 *     "demo-tenant",
 *     Set.of("RECRUITER"),
 *     Set.of("tools.read", "tools.write"),
 *     AuthenticationType.NONE);
 * }</pre>
 *
 * @param subject stable user or service subject
 * @param tenantId mandatory tenant isolation key
 * @param roles application roles
 * @param scopes OAuth2 / tool scopes
 * @param authenticationType how identity was established
 */
public record CallerIdentity(
        String subject,
        String tenantId,
        Set<String> roles,
        Set<String> scopes,
        AuthenticationType authenticationType) {}
