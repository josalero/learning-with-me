package dev.mytechprofile.aigateway.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.mytechprofile.aigateway.shared.api.GatewayException;

/**
 * Static access to the authenticated {@link GatewayPrincipal} of the current request thread.
 *
 * {@snippet :
 * GatewayPrincipal principal = CurrentPrincipal.get();
 * executions.accept(principal, request, traceId, Context.current());
 * }
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {}

    /** The principal set by {@link ApiKeyAuthenticationFilter}; {@code 401} if the request is not authenticated. */
    public static GatewayPrincipal get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayPrincipal principal)) {
            throw new GatewayException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal;
    }
}
