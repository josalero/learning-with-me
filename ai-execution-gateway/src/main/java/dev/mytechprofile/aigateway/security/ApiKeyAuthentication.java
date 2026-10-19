package dev.mytechprofile.aigateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Security token whose credentials are empty. The raw API key is not retained.
 */
public final class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final GatewayPrincipal principal;

    public ApiKeyAuthentication(GatewayPrincipal principal) {
        super(principal.scopes().stream().map(SimpleGrantedAuthority::new).toList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
