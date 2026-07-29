package dev.mytechprofile.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for the MCP Integration Gateway framework runtime.
 *
 * <p><strong>When to use:</strong> start this application to expose configured
 * legacy operations as MCP tools over Streamable HTTP.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * jenv shell 26
 * ./gradlew :gateway:bootRun
 * # MCP endpoint: http://127.0.0.1:8080/mcp
 * }</pre>
 *
 * <p>Component scanning includes {@code dev.mytechprofile.gateway} so connector
 * implementations under {@code connector.openapi} are registered automatically.
 */
@SpringBootApplication
@ComponentScan(basePackages = "dev.mytechprofile.gateway")
public class GatewayApplication {

    /**
     * Boots the gateway.
     *
     * @param args Spring Boot arguments; use {@code --spring.profiles.active=local}
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
