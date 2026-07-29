package dev.mytechprofile.gateway.transport;

import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.pipeline.ToolAuthorizationManager;
import dev.mytechprofile.gateway.pipeline.ToolInvocationPipeline;
import dev.mytechprofile.gateway.security.CallerIdentityResolver;

/**
 * Wires the compiled tool catalog into Spring AI's MCP server auto-configuration.
 *
 * <p>Spring AI aggregates tool callbacks at server startup. Under local/restricted
 * profiles the synthetic identity filters the published set. Under JWT, identity is
 * request-scoped so aggregation cannot resolve a caller — all compiled tools are
 * published and {@link ToolAuthorizationManager} enforces AuthZ on invocation.
 */
@Configuration
public class McpTransportConfiguration {

    @Bean
    ToolCallbackProvider gatewayTools(
            ToolRegistry registry,
            ToolInvocationPipeline pipeline,
            ToolAuthorizationManager authorizationManager,
            CallerIdentityResolver identityResolver,
            ObjectMapper mapper) {
        return () -> registry.all().stream()
                .filter(tool -> {
                    try {
                        return authorizationManager.isAllowed(tool, identityResolver.resolve());
                    } catch (GatewayException ex) {
                        return true;
                    }
                })
                .map(tool -> (ToolCallback) new CatalogToolCallback(tool, pipeline, mapper))
                .toArray(ToolCallback[]::new);
    }
}
