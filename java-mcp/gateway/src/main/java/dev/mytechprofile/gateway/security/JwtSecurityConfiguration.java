package dev.mytechprofile.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;

/**
 * Inbound JWT resource-server security.
 *
 * <p>Uses an HMAC secret for local demos so Compose does not require a full IdP.
 */
@Configuration
@Profile("jwt")
public class JwtSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${gateway.security.jwt.hmac-secret:local-demo-hmac-secret-change-me-32bytes}")
                    String hmacSecret,
            @Value("${gateway.security.jwt.audience:mcp-integration-gateway}") String audience) {
        byte[] secret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            if (token.getAudience() != null && token.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "required audience missing", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, audienceValidator));
        return decoder;
    }

    @Bean
    CallerIdentityResolver jwtIdentityResolver(
            @Value("${gateway.security.jwt.audience:mcp-integration-gateway}") String audience) {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
                throw new GatewayException(new GatewayError.AccessDenied("missing bearer token"));
            }
            List<String> audiences = jwt.getAudience();
            if (audiences == null || audiences.stream().noneMatch(audience::equals)) {
                throw new GatewayException(new GatewayError.AccessDenied("token audience rejected"));
            }
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId == null || tenantId.isBlank()) {
                throw new GatewayException(new GatewayError.AccessDenied("token has no tenant claim"));
            }
            Set<String> roles = claimSet(jwt, "roles");
            Set<String> scopes = scopesFrom(jwt);
            return new CallerIdentity(
                    jwt.getSubject(), tenantId, roles, scopes, AuthenticationType.OAUTH2_JWT);
        };
    }

    @Bean
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                        .permitAll()
                        .requestMatchers("/mcp", "/mcp/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    private static Set<String> claimSet(Jwt jwt, String claim) {
        Object value = jwt.getClaim(claim);
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.toCollection(HashSet::new));
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split("[\\s,]")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static Set<String> scopesFrom(Jwt jwt) {
        Set<String> scopes = new HashSet<>(claimSet(jwt, "scopes"));
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            Arrays.stream(scope.split("\\s+")).filter(s -> !s.isBlank()).forEach(scopes::add);
        }
        return Set.copyOf(scopes);
    }
}
