package dev.mytechprofile.aigateway.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless security: every endpoint except health and info needs a valid {@code X-API-Key}; failures are a {@code 401} problem.
 */
@Configuration
public class SecurityConfig {

    @Bean
    ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
            ApiKeyRepository apiKeys, PrincipalRepository principals, JsonMapper json) {
        return new ApiKeyAuthenticationFilter(apiKeys, principals, json);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeys, JsonMapper json)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    json.writeValue(
                            response.getOutputStream(),
                            ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required"));
                }))
                .addFilterBefore(apiKeys, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
