package dev.mytechprofile.gateway.examples.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Domain REST API that integrates through MCP tools (instead of Feign/RestClient).
 *
 * <p>Outward: HTTP/JSON for callers. Inward: Spring AI MCP client → gateway {@code /mcp}.
 */
@SpringBootApplication
public class OrchestratorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApiApplication.class, args);
    }
}
