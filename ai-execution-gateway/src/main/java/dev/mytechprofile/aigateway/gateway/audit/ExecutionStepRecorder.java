package dev.mytechprofile.aigateway.gateway.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.gateway.authority.Decision;
import dev.mytechprofile.aigateway.gateway.authority.ReasonCode;
import dev.mytechprofile.aigateway.gateway.execution.ExecutionEntity;
import dev.mytechprofile.aigateway.gateway.model.ModelTurn;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/** Writes one audit row and one OpenTelemetry span per model turn or tool decision. */
@Component
public class ExecutionStepRecorder {

    private final ExecutionStepRepository steps;
    private final AuditHasher hasher;
    private final Tracer tracer;
    private final Clock clock;

    public ExecutionStepRecorder(
            ExecutionStepRepository steps, AuditHasher hasher, Tracer tracer, Clock clock) {
        this.steps = steps;
        this.hasher = hasher;
        this.tracer = tracer;
        this.clock = clock;
    }

    /** Stores one model turn with its token usage and cost, inside an {@code ai.model.call} span. */
    public void modelStep(ExecutionEntity execution, int seq, String modelId, ModelTurn turn, long costMicros) {
        Span span = spanFor("ai.model.call", execution)
                .setAttribute("gen_ai.request.model", modelId)
                .setAttribute("gen_ai.usage.input_tokens", turn.inputTokens())
                .setAttribute("gen_ai.usage.output_tokens", turn.outputTokens())
                .startSpan();
        ExecutionStepEntity step = base(execution.getId(), seq, StepType.MODEL, span);
        step.setInputTokens(turn.inputTokens());
        step.setOutputTokens(turn.outputTokens());
        step.setCostMicros(costMicros);
        saveWithin(span, step);
    }

    /**
     * Stores one tool decision inside an {@code ai.tool.call} span. Arguments and result are kept only as
     * HMACs, so the audit can prove what was sent without storing candidate data.
     */
    public void toolStep(
            ExecutionEntity execution,
            int seq,
            String toolName,
            Decision decision,
            ReasonCode reason,
            String argumentsJson,
            String resultJson) {
        Span span = spanFor("ai.tool.call", execution)
                .setAttribute("tool.name", toolName)
                .setAttribute("tool.decision", decision.name())
                .setAttribute("tool.reason_code", reason.name())
                .startSpan();
        ExecutionStepEntity step = base(execution.getId(), seq, StepType.TOOL, span);
        step.setToolName(toolName);
        step.setDecision(decision);
        step.setReasonCode(reason);
        step.setArgsHmac(hasher.hmac(argumentsJson));
        step.setResultHmac(hasher.hmac(resultJson));
        step.setInputTokens(0);
        step.setOutputTokens(0);
        step.setCostMicros(0L);
        saveWithin(span, step);
    }

    /** Every step span carries who acted and for whom, so a trace can be read without the database. */
    private SpanBuilder spanFor(String name, ExecutionEntity execution) {
        return tracer.spanBuilder(name)
                .setAttribute("agent.id", execution.getAgentId())
                .setAttribute("delegator.id", execution.getPrincipalId())
                .setAttribute("execution.id", execution.getId().toString());
    }

    private void saveWithin(Span span, ExecutionStepEntity step) {
        try (Scope ignored = span.makeCurrent()) {
            steps.save(step);
        } finally {
            span.end();
        }
    }

    private ExecutionStepEntity base(UUID executionId, int seq, StepType type, Span span) {
        ExecutionStepEntity step = new ExecutionStepEntity();
        step.setId(UUID.randomUUID());
        step.setExecutionId(executionId);
        step.setSeq(seq);
        step.setType(type);
        step.setSpanId(span.getSpanContext().getSpanId());
        step.setCreatedAt(clock.instant());
        return step;
    }
}
