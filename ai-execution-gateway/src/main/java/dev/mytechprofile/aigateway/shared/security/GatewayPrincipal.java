package dev.mytechprofile.aigateway.shared.security;

import java.util.Set;

/**
 * Authenticated caller. Tenant and scopes come from the server-side principal row.
 */
public record GatewayPrincipal(String id, PrincipalKind kind, String tenantId, Set<String> scopes) {}
