package dev.mytechprofile.gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound gateway configuration (connections, tools, security knobs).
 *
 * <p><strong>When to use:</strong> loaded from {@code gateway/gateway.yml} and
 * catalog packs via {@code spring.config.import}. Consumed by {@link ToolCompiler}.
 *
 * <p><strong>Example:</strong> see {@code classpath:gateway/catalog/recruiting/tools.yml}.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        int configurationVersion,
        SecurityProperties security,
        ObservabilityProperties observability,
        Map<String, ConnectionProperties> connections,
        Map<String, ToolProperties> tools) {

    public GatewayProperties {
        connections = connections == null ? Map.of() : Map.copyOf(connections);
        tools = tools == null ? Map.of() : Map.copyOf(tools);
    }

    public record SecurityProperties(InboundProperties inbound, JwtProperties jwt) {
        public record InboundProperties(String type) {}

        public record JwtProperties(String hmacSecret, String audience) {}
    }

    public record ObservabilityProperties(boolean auditEnabled, boolean logToolArguments) {}

    public record ConnectionProperties(
            String type,
            String specification,
            String baseUrl,
            String datasource,
            AuthenticationProperties authentication,
            TimeoutProperties timeouts,
            Map<String, SqlOperationProperties> operations,
            Map<String, Object> settings) {}

    public record AuthenticationProperties(
            String type, String token, String tokenUri, String clientId, String clientSecret) {}

    public record TimeoutProperties(Duration connect, Duration read) {}

    public record SqlOperationProperties(
            String sql,
            Map<String, Map<String, Object>> parameters,
            Integer maxRows,
            Duration timeout) {}

    public record ToolProperties(
            String connection,
            String operation,
            String mode,
            String description,
            InputProperties input,
            Map<String, String> contextMappings,
            OutputProperties output,
            AuthorizationProperties authorization,
            QuotaProperties quota,
            boolean requiresApproval,
            String owner,
            String version,
            boolean deprecated) {}

    public record InputProperties(
            List<String> include, Map<String, Object> defaults, Map<String, Map<String, Object>> override) {}

    public record OutputProperties(List<String> include, List<String> mask, Integer maximumItems) {}

    public record AuthorizationProperties(List<String> requiredScopes, List<String> requiredRoles) {}

    public record QuotaProperties(String perSubject) {}
}
