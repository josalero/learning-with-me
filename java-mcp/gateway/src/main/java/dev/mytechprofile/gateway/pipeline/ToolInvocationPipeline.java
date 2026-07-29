package dev.mytechprofile.gateway.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.mytechprofile.gateway.audit.ArgumentHasher;
import dev.mytechprofile.gateway.audit.ToolInvocationCompleted;
import dev.mytechprofile.gateway.connector.Connector;
import dev.mytechprofile.gateway.connector.ExecutionResult;
import dev.mytechprofile.gateway.connector.OperationInvocation;
import dev.mytechprofile.gateway.connector.ToolExecutionContext;
import dev.mytechprofile.gateway.model.CompiledTool;
import dev.mytechprofile.gateway.model.GatewayError;
import dev.mytechprofile.gateway.model.GatewayException;
import dev.mytechprofile.gateway.model.ToolResult;
import dev.mytechprofile.gateway.security.CallerIdentityResolver;

/**
 * Executes a compiled tool through the governance pipeline.
 *
 * <p>Order: validate → authorize → quota → inject → approval → execute → project → audit.
 */
@Component
public class ToolInvocationPipeline {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationPipeline.class);

    private final Map<String, Connector> connectorsByType;
    private final CallerIdentityResolver identityResolver;
    private final ArgumentValidator argumentValidator;
    private final ToolAuthorizationManager authorizationManager;
    private final QuotaGate quotaGate;
    private final TrustedContextInjector contextInjector;
    private final ApprovalGate approvalGate;
    private final OutputProjector outputProjector;
    private final ArgumentHasher argumentHasher;
    private final ApplicationEventPublisher events;

    public ToolInvocationPipeline(
            List<Connector> connectors,
            CallerIdentityResolver identityResolver,
            ArgumentValidator argumentValidator,
            ToolAuthorizationManager authorizationManager,
            QuotaGate quotaGate,
            TrustedContextInjector contextInjector,
            ApprovalGate approvalGate,
            OutputProjector outputProjector,
            ArgumentHasher argumentHasher,
            ApplicationEventPublisher events) {
        this.connectorsByType = connectors.stream()
                .collect(Collectors.toUnmodifiableMap(Connector::type, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("duplicate connector type: " + a.type());
                }));
        this.identityResolver = identityResolver;
        this.argumentValidator = argumentValidator;
        this.authorizationManager = authorizationManager;
        this.quotaGate = quotaGate;
        this.contextInjector = contextInjector;
        this.approvalGate = approvalGate;
        this.outputProjector = outputProjector;
        this.argumentHasher = argumentHasher;
        this.events = events;
    }

    public ToolResult invoke(CompiledTool tool, Map<String, Object> arguments, ToolContext toolContext) {
        ToolExecutionContext context = ToolExecutionContext.start(identityResolver.resolve());
        Map<String, Object> enriched = Map.of();
        int removedFields = 0;
        AtomicReference<String> authorizationDecision = new AtomicReference<>("ALLOW");
        AtomicReference<String> approvalOutcome = new AtomicReference<>("NOT_REQUIRED");
        try {
            Map<String, Object> validated = argumentValidator.validate(tool, arguments);
            authorizationManager.authorize(tool, context);
            quotaGate.consume(tool, context);
            enriched = contextInjector.inject(tool, validated, context);
            log.debug(
                    "tool='{}' injected trustedContextFields={}",
                    tool.name(),
                    tool.contextMappings().keySet());

            approvalOutcome.set(approvalGate.require(tool, enriched, context, toolContext));

            Connector connector = connectorsByType.get(tool.connection().type());
            if (connector == null) {
                throw new IllegalStateException("no connector for type: " + tool.connection().type());
            }
            OperationInvocation invocation =
                    new OperationInvocation(tool.connection(), tool.operation(), enriched);
            ExecutionResult execution = connector.execute(invocation, context);
            ToolResult result = ToolResult.of(execution);
            if (result.success()) {
                OutputProjector.ProjectionResult projected =
                        outputProjector.apply(tool.outputPolicy(), result.body());
                removedFields = projected.removedFieldCount();
                result = new ToolResult(true, projected.payload(), null);
            }
            publishAudit(
                    tool,
                    context,
                    enriched,
                    result,
                    removedFields,
                    authorizationDecision.get(),
                    approvalOutcome.get());
            return result;
        } catch (GatewayException ex) {
            if (ex.error() instanceof GatewayError.AccessDenied) {
                authorizationDecision.set(
                        tool.requiresApproval() && approvalOutcome.get().equals("DECLINED")
                                ? "ALLOW"
                                : "DENY");
                if ("approval declined by user".equals(ex.error().message())
                        || ex.error().message().contains("elicitation")
                        || ex.error().message().contains("approval required")) {
                    authorizationDecision.set("ALLOW");
                    if (!"ACCEPTED".equals(approvalOutcome.get())) {
                        approvalOutcome.set(
                                ex.error().message().contains("declined") ? "DECLINED" : "UNAVAILABLE");
                    }
                }
            } else if (ex.error() instanceof GatewayError.QuotaExceeded) {
                authorizationDecision.set("ALLOW");
            }
            ToolResult result = ToolResult.error(ex.error());
            publishAudit(
                    tool,
                    context,
                    enriched,
                    result,
                    removedFields,
                    authorizationDecision.get(),
                    approvalOutcome.get());
            return result;
        } catch (RuntimeException ex) {
            log.error(
                    "unexpected connector/pipeline failure tool='{}' invocationId='{}'",
                    tool.name(),
                    context.invocationId(),
                    ex);
            ToolResult result = ToolResult.error(new GatewayError.PermanentConnectorFailure(
                    tool.operation(), "unexpected downstream execution failure"));
            publishAudit(
                    tool,
                    context,
                    enriched,
                    result,
                    removedFields,
                    authorizationDecision.get(),
                    approvalOutcome.get());
            return result;
        }
    }

    private void publishAudit(
            CompiledTool tool,
            ToolExecutionContext context,
            Map<String, Object> enriched,
            ToolResult result,
            int removedFields,
            String authorizationDecision,
            String approvalOutcome) {
        String category = result.success()
                ? "SUCCESS"
                : result.error() instanceof GatewayError.QuotaExceeded ? "QUOTA" : "ERROR";
        String errorReference = result.success() ? null : result.error().type();
        events.publishEvent(new ToolInvocationCompleted(
                context.invocationId(),
                context.correlationId(),
                null,
                tool.name(),
                tool.connection().name(),
                tool.connection().type(),
                context.identity().subject(),
                context.identity().tenantId(),
                context.identity().authenticationType(),
                authorizationDecision,
                approvalOutcome,
                argumentHasher.hash(enriched),
                context.startedAt(),
                Instant.now(),
                category,
                removedFields,
                errorReference));
    }
}
