package dev.mytechprofile.gateway.connector.openapi;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import dev.mytechprofile.gateway.connector.ConnectionBearerTokenResolver;

/**
 * Makes the OpenAPI connector installable by adding its JAR to a Spring Boot gateway.
 */
@AutoConfiguration
public class OpenApiConnectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpenApiConnector openApiConnector(
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            ObjectProvider<ConnectionBearerTokenResolver> bearerTokenResolver) {
        return new OpenApiConnector(objectMapper, restClientBuilder, bearerTokenResolver);
    }
}
