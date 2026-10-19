package dev.mytechprofile.aigateway.security;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.mytechprofile.aigateway.persistence.ApiKeyRepository;
import dev.mytechprofile.aigateway.persistence.PrincipalRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticates {@code X-API-Key}. A missing, malformed, or unknown key is 401.
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
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health") || path.startsWith("/actuator/info")) {
            chain.doFilter(request, response);
            return;
        }
        String raw = request.getHeader(HEADER);
        if (raw == null || !KEY.matcher(raw).matches()) {
            unauthorized(response);
            return;
        }
        String computed = ApiKeyHasher.sha256(raw);
        var key = apiKeys.findByKeyHashAndRevokedAtIsNull(computed).orElse(null);
        if (key == null || !ApiKeyHasher.matches(key.getKeyHash(), computed)) {
            unauthorized(response);
            return;
        }
        var principal = principals.findById(key.getPrincipalId()).orElse(null);
        if (principal == null) {
            unauthorized(response);
            return;
        }
        var gatewayPrincipal = new GatewayPrincipal(
                principal.getId(),
                principal.getKind(),
                principal.getTenantId(),
                Set.copyOf(principal.getScopes()));
        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(gatewayPrincipal));
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
