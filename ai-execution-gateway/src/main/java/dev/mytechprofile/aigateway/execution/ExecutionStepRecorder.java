package dev.mytechprofile.aigateway.execution;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.audit.AuditHasher;
import dev.mytechprofile.aigateway.domain.Decision;
import dev.mytechprofile.aigateway.domain.ReasonCode;
import dev.mytechprofile.aigateway.domain.StepType;
import dev.mytechprofile.aigateway.model.ModelTurn;
import dev.mytechprofile.aigateway.persistence.ExecutionEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionStepEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionStepRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

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

    public void modelStep(ExecutionEntity execution, int seq, String modelId, ModelTurn turn, long costMicros) {
        Span span = tracer.spanBuilder("ai.model.call")
                .setAttribute("gen_ai.request.model", modelId)
                .setAttribute("gen_ai.usage.input_tokens", turn.inputTokens())
                .setAttribute("gen_ai.usage.output_tokens", turn.outputTokens())
                .setAttribute("agent.id", execution.getAgentId())
                .setAttribute("delegator.id", execution.getPrincipalId())
                .setAttribute("execution.id", execution.getId().toString())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ExecutionStepEntity step = base(execution.getId(), seq, StepType.MODEL, span);
            step.setInputTokens(turn.inputTokens());
            step.setOutputTokens(turn.outputTokens());
            step.setCostMicros(costMicros);
            steps.save(step);
        } finally {
            span.end();
        }
    }

    public void toolStep(
            ExecutionEntity execution,
            int seq,
            String toolName,
            Decision decision,
            ReasonCode reason,
            String argumentsJson,
            String resultJson) {
        Span span = tracer.spanBuilder("ai.tool.call")
                .setAttribute("tool.name", toolName)
                .setAttribute("tool.decision", decision.name())
                .setAttribute("tool.reason_code", reason.name())
                .setAttribute("agent.id", execution.getAgentId())
                .setAttribute("delegator.id", execution.getPrincipalId())
                .setAttribute("execution.id", execution.getId().toString())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ExecutionStepEntity step = base(execution.getId(), seq, StepType.TOOL, span);
            step.setToolName(toolName);
            step.setDecision(decision);
            step.setReasonCode(reason);
            step.setArgsHmac(hasher.hmac(argumentsJson));
            step.setResultHmac(hasher.hmac(resultJson));
            step.setInputTokens(0);
            step.setOutputTokens(0);
            step.setCostMicros(0L);
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
