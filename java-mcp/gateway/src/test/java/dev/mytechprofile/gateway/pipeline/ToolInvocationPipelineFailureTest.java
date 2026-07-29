package dev.mytechprofile.gateway.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.mytechprofile.gateway.audit.ArgumentHasher;
import dev.mytechprofile.gateway.audit.ToolInvocationCompleted;
import dev.mytechprofile.gateway.connector.AuthenticationType;
import dev.mytechprofile.gateway.connector.CallerIdentity;
import dev.mytechprofile.gateway.connector.ConnectionDefinition;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ConnectorCapabilities;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationDescriptor;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.AuthorizationPolicy;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.OutputPolicy;
import dev.mytechprofile.gateway.model.QuotaPolicy;
import dev.mytechprofile.gateway.model.ToolMode;

class ToolInvocationPipelineFailureTest {

    @Test
    void normalizesUnexpectedConnectorExceptionAndPublishesAudit() {
        ObjectMapper mapper = new ObjectMapper();
        Connector connector = new Connector() {
            public String type() {
                return "broken";
            }

            public ConnectorCapabilities capabilities() {
                return ConnectorCapabilities.declaredOnly();
            }

            public List<OperationDescriptor> discover(ConnectionDefinition connection) {
                return List.of();
            }

            public ExecutionResult execute(
                    OperationInvocation invocation, ToolExecutionContext context) {
                throw new IllegalStateException("secret implementation detail");
            }
        };
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ToolInvocationPipeline pipeline = new ToolInvocationPipeline(
                List.of(connector),
                () -> new CallerIdentity(
                        "subject",
                        "tenant",
                        Set.of(),
                        Set.of(),
                        AuthenticationType.NONE),
                new ArgumentValidator(),
                new ToolAuthorizationManager(),
                new QuotaGate(),
                new TrustedContextInjector(),
                new ApprovalGate(),
                new OutputProjector(mapper),
                new ArgumentHasher(mapper, "test-salt"),
                events);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties");
        CompiledTool tool = new CompiledTool(
                "broken_tool",
                "Broken",
                new ConnectionDefinition(
                        "broken",
                        "broken",
                        URI.create("about:blank"),
                        null,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)),
                "explode",
                ToolMode.READ,
                schema,
                Map.of(),
                Map.of(),
                OutputPolicy.none(),
                AuthorizationPolicy.none(),
                QuotaPolicy.none(),
                false);

        var result = pipeline.invoke(tool, Map.of(), null);

        assertThat(result.success()).isFalse();
        assertThat(result.error().type()).isEqualTo("connector_failure");
        assertThat(result.error().message()).doesNotContain("secret implementation detail");
        verify(events).publishEvent(any(ToolInvocationCompleted.class));
    }
}
