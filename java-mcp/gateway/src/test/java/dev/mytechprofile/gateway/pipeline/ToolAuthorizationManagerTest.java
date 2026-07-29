package dev.mytechprofile.gateway.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.AuthorizationPolicy;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.OutputPolicy;
import dev.mytechprofile.gateway.model.QuotaPolicy;
import dev.mytechprofile.gateway.model.ToolMode;

class ToolAuthorizationManagerTest {

    private final ToolAuthorizationManager manager = new ToolAuthorizationManager();

    @Test
    void authorize_deniesMissingRole() {
        CompiledTool tool = tool(new AuthorizationPolicy(Set.of("tools.read").stream().toList(), Set.of("RECRUITER").stream().toList()));
        ToolExecutionContext context = ToolExecutionContext.start(new CallerIdentity(
                "restricted-user",
                "demo-tenant",
                Set.of("VIEWER"),
                Set.of("tools.read"),
                AuthenticationType.NONE));

        assertThat(manager.isAllowed(tool, context.identity())).isFalse();
        assertThatThrownBy(() -> manager.authorize(tool, context)).isInstanceOf(GatewayException.class);
    }

    @Test
    void authorize_allowsMatchingIdentity() {
        CompiledTool tool = tool(new AuthorizationPolicy(java.util.List.of("tools.read"), java.util.List.of("RECRUITER")));
        CallerIdentity identity = new CallerIdentity(
                "local-developer",
                "demo-tenant",
                Set.of("RECRUITER"),
                Set.of("tools.read"),
                AuthenticationType.NONE);
        assertThat(manager.isAllowed(tool, identity)).isTrue();
    }

    private CompiledTool tool(AuthorizationPolicy policy) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        return new CompiledTool(
                "search_candidates",
                "Search",
                new ConnectionDefinition(
                        "recruiting-api",
                        "openapi",
                        URI.create("http://127.0.0.1:9080"),
                        null,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2)),
                "searchCandidates",
                ToolMode.READ,
                schema,
                Map.of(),
                Map.of(),
                OutputPolicy.none(),
                policy,
                QuotaPolicy.none(),
                false);
    }
}
