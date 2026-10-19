package dev.mytechprofile.aigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the AI Execution Gateway lab.
 *
 * <p>The default profile is {@code stub}: a scripted model, no network calls. Start the whole stack with
 * Docker and run a scenario:
 * {@snippet lang=shell :
 * cp .env.example .env
 * docker compose up -d --build
 * ./scripts/run-scenario.sh 1
 * }
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiGatewayApplication {

    /** Starts Spring Boot. Set {@code SPRING_PROFILES_ACTIVE=live} to use the real model. */
    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
