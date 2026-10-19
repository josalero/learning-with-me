package dev.mytechprofile.aigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI execution gateway. Callers delegate to an agent; the application authorizes every tool call.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
