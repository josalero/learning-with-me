package dev.mytechprofile.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import dev.mytechprofile.gateway.connector.ConnectionBearerTokenResolver;

@Configuration
public class OutboundAuthConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConnectionBearerTokenResolver.class)
    ConnectionBearerTokenResolver connectionBearerTokenResolver(OutboundBearerTokenService service) {
        return service::resolveBearerToken;
    }
}
