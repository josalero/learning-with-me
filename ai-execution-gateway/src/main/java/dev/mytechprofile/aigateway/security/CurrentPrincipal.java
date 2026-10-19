package dev.mytechprofile.aigateway.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.mytechprofile.aigateway.api.GatewayException;

public final class CurrentPrincipal {

    private CurrentPrincipal() {}

    public static GatewayPrincipal get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayPrincipal principal)) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal;
    }
}
