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
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.OutputPolicy;
import dev.mytechprofile.gateway.model.QuotaPolicy;
import dev.mytechprofile.gateway.model.ToolMode;

class QuotaGateTest {

    @Test
    void consume_exhaustsPerSubjectBudget() {
        QuotaGate gate = new QuotaGate();
        CompiledTool tool = tool(QuotaPolicy.parse("2/1h"));
        ToolExecutionContext context = ToolExecutionContext.start(new CallerIdentity(
                "local-developer",
                "demo-tenant",
                Set.of("RECRUITER"),
                Set.of("tools.read"),
                AuthenticationType.NONE));

        gate.consume(tool, context);
        gate.consume(tool, context);
        assertThatThrownBy(() -> gate.consume(tool, context))
                .isInstanceOf(GatewayException.class)
                .extracting(ex -> ((GatewayException) ex).error())
                .isInstanceOf(GatewayError.QuotaExceeded.class);
        assertThat(gate.remaining("local-developer", "search_candidates")).isEqualTo(0);
    }

    private CompiledTool tool(QuotaPolicy policy) {
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
                AuthorizationPolicy.none(),
                policy,
                false);
    }
}
