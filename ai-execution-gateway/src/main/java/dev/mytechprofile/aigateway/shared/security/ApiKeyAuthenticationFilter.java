package dev.mytechprofile.aigateway.shared.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticates {@code X-API-Key}. A missing, malformed, or unknown key is 401.
 *
 * <p>The key shape is {@code gw_} plus 43 base64url characters. Only its SHA-256 is stored, and the
 * principal (id, kind, tenant, scopes) comes from the database, never from the request.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private static final Pattern KEY = Pattern.compile("^gw_[A-Za-z0-9_-]{43}$");

    private final ApiKeyRepository apiKeys;
    private final PrincipalRepository principals;
    private final JsonMapper json;

    public ApiKeyAuthenticationFilter(
            ApiKeyRepository apiKeys, PrincipalRepository principals, JsonMapper json) {
        this.apiKeys = apiKeys;
        this.principals = principals;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublicPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Optional<GatewayPrincipal> principal = resolve(request.getHeader(HEADER));
        if (principal.isEmpty()) {
            unauthorized(response);
            return;
        }
        continueAs(principal.get(), request, response, chain);
    }

    private static boolean isPublicPath(String path) {
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/info");
    }

    /** Empty when the key is missing, malformed, unknown, revoked, or points at a deleted principal. */
    private Optional<GatewayPrincipal> resolve(String rawKey) {
        if (rawKey == null || !KEY.matcher(rawKey).matches()) {
            return Optional.empty();
        }
        String computed = ApiKeyHasher.sha256(rawKey);
        return apiKeys.findByKeyHashAndRevokedAtIsNull(computed)
                .filter(key -> ApiKeyHasher.matches(key.getKeyHash(), computed))
                .flatMap(key -> principals.findById(key.getPrincipalId()))
                .map(row -> new GatewayPrincipal(
                        row.getId(), row.getKind(), row.getTenantId(), Set.copyOf(row.getScopes())));
    }

    private static void continueAs(
            GatewayPrincipal principal, HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(principal));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication is required");
        json.writeValue(response.getOutputStream(), problem);
    }
}
