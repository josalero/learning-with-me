package dev.mytechprofile.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.model.GatewayException;

class JwtIdentityResolverTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolve_rejectsMissingJwtPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("anon", "n/a"));
        CallerIdentityResolver resolver =
                new JwtSecurityConfiguration().jwtIdentityResolver("mcp-integration-gateway");
        assertThatThrownBy(resolver::resolve).isInstanceOf(GatewayException.class);
    }

    @Test
    void resolve_rejectsWrongAudience() {
        Jwt jwt = jwt(Map.of("tenant_id", "demo-tenant", "roles", List.of("RECRUITER"), "scope", "tools.read"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        CallerIdentityResolver resolver =
                new JwtSecurityConfiguration().jwtIdentityResolver("mcp-integration-gateway");
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void resolve_rejectsMissingTenant() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user-1")
                .audience(List.of("mcp-integration-gateway"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "tools.read")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        CallerIdentityResolver resolver =
                new JwtSecurityConfiguration().jwtIdentityResolver("mcp-integration-gateway");
        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void resolve_acceptsValidClaims() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user-1")
                .audience(List.of("mcp-integration-gateway"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("tenant_id", "demo-tenant")
                .claim("roles", List.of("RECRUITER"))
                .claim("scope", "tools.read tools.write")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        CallerIdentity identity =
                new JwtSecurityConfiguration().jwtIdentityResolver("mcp-integration-gateway").resolve();
        assertThat(identity.subject()).isEqualTo("user-1");
        assertThat(identity.tenantId()).isEqualTo("demo-tenant");
        assertThat(identity.roles()).contains("RECRUITER");
        assertThat(identity.scopes()).contains("tools.read", "tools.write");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("user-1")
                .audience(List.of("other-audience"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
