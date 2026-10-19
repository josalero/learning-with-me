package dev.mytechprofile.aigateway.security;

import java.util.Set;

import dev.mytechprofile.aigateway.domain.PrincipalKind;

/**
 * Authenticated caller. Tenant and scopes come from the server-side principal row.
 */
public record GatewayPrincipal(String id, PrincipalKind kind, String tenantId, Set<String> scopes) {}
