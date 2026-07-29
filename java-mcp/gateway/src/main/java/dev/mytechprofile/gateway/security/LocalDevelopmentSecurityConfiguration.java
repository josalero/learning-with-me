package dev.mytechprofile.gateway.security;

import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;

/**
 * Local-development security: synthetic identity and open MCP endpoints.
 */
@Configuration
@Profile("!jwt")
public class LocalDevelopmentSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LocalDevelopmentSecurityConfiguration.class);

    @Bean
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                        .permitAll()
                        .requestMatchers("/mcp", "/mcp/**")
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .build();
    }

    /**
     * Synthetic identity fixed at startup from active profiles.
     *
     * <p>Activate {@code restricted} (for example via {@code docker-compose.restricted.yml})
     * to drop {@code RECRUITER} and {@code tools.write}.
     */
    @Bean
    CallerIdentityResolver localIdentityResolver(Environment environment) {
        boolean restricted = Arrays.asList(environment.getActiveProfiles()).contains("restricted");
        CallerIdentity identity = restricted ? restrictedIdentity() : fullIdentity();
        log.info(
                "synthetic identity subject={} restricted={} activeProfiles={}",
                identity.subject(),
                restricted,
                String.join(",", environment.getActiveProfiles()));
        return () -> identity;
    }

    private static CallerIdentity fullIdentity() {
        return new CallerIdentity(
                "local-developer",
                "demo-tenant",
                Set.of("RECRUITER", "DEVELOPER"),
                Set.of("tools.read", "tools.write"),
                AuthenticationType.NONE);
    }

    private static CallerIdentity restrictedIdentity() {
        return new CallerIdentity(
                "restricted-user",
                "demo-tenant",
                Set.of("VIEWER"),
                Set.of("tools.read"),
                AuthenticationType.NONE);
    }
}
