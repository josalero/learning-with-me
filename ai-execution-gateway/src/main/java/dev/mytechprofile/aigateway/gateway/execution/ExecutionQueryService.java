package dev.mytechprofile.aigateway.gateway.execution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.aigateway.gateway.audit.ExecutionStepEntity;
import dev.mytechprofile.aigateway.gateway.audit.ExecutionStepRepository;
import dev.mytechprofile.aigateway.gateway.audit.StepType;
import dev.mytechprofile.aigateway.gateway.authority.Decision;
import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestRepository;
import dev.mytechprofile.aigateway.shared.api.DelegatorView;
import dev.mytechprofile.aigateway.shared.api.EvaluationView;
import dev.mytechprofile.aigateway.shared.api.ExecutionView;
import dev.mytechprofile.aigateway.shared.api.GatewayException;
import dev.mytechprofile.aigateway.shared.api.ReconstructionView;
import dev.mytechprofile.aigateway.shared.api.StepView;
import dev.mytechprofile.aigateway.shared.api.TotalsView;
import dev.mytechprofile.aigateway.shared.api.TransitionRequestView;
import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;
import dev.mytechprofile.aigateway.shared.security.PrincipalRepository;

/**
 * Read side of executions: polling, reconstruction, and the transition requests of a run.
 *
 * <p>Every method scopes by the caller's tenant; another tenant's execution is {@code 404}, as if it did not exist.
 */
@Service
public class ExecutionQueryService {

    private final ExecutionRepository executions;
    private final ExecutionStepRepository steps;
    private final PrincipalRepository principals;
    private final TransitionRequestRepository transitions;

    public ExecutionQueryService(
            ExecutionRepository executions,
            ExecutionStepRepository steps,
            PrincipalRepository principals,
            TransitionRequestRepository transitions) {
        this.executions = executions;
        this.steps = steps;
        this.principals = principals;
        this.transitions = transitions;
    }

    /** Current status of one execution; poll this until the status is terminal. */
    @Transactional(readOnly = true)
    public ExecutionView get(GatewayPrincipal principal, UUID executionId) {
        ExecutionEntity execution = require(principal, executionId);
        return new ExecutionView(
                execution.getId(),
                execution.getStatus(),
                execution.getAgentId(),
                execution.getPrincipalId(),
                evaluation(execution),
                execution.getCreatedAt(),
                execution.getCompletedAt());
    }

    /**
     * Rebuilds who acted and what happened, from stored steps only. Contains HMACs and counts, never
     * arguments, results, prompts, or candidate text. A foreign execution is {@code 404}.
     */
    @Transactional(readOnly = true)
    public ReconstructionView reconstruct(GatewayPrincipal principal, UUID executionId) {
        ExecutionEntity execution = require(principal, executionId);
        List<ExecutionStepEntity> rows = steps.findByExecutionIdOrderBySeqAsc(executionId);
        return new ReconstructionView(
                execution.getId(),
                execution.getTraceId(),
                execution.getAgentId(),
                delegatorOf(execution),
                execution.getStatus(),
                rows.stream().map(ExecutionQueryService::toStepView).toList(),
                Totals.of(rows),
                evaluation(execution));
    }

    /**
     * Lists status-change requests created by this execution.
     *
     * <p>Call it after the execution is terminal, with the same API key that started it. A foreign
     * execution is {@code 404}. The list has ids only, so a client can approve without reading the
     * database.
     *
     * <pre>{@code
     * GET /api/v1/executions/1d3c2a7e-4b18-4d6a-9c2e-6f0a1b2c3d4e/transition-requests
     * [{"transitionRequestId":"...","status":"PENDING","from":"SCREENING","to":"INTERVIEW"}]
     * }</pre>
     */
    @Transactional(readOnly = true)
    public List<TransitionRequestView> transitionRequests(GatewayPrincipal principal, UUID executionId) {
        require(principal, executionId);
        return transitions.findByExecutionId(executionId).stream()
                .map(row -> new TransitionRequestView(
                        row.getId(), row.getStatus().name(), row.getFromStatus().name(), row.getToStatus().name()))
                .toList();
    }

    private ExecutionEntity require(GatewayPrincipal principal, UUID executionId) {
        return executions
                .findByIdAndTenantId(executionId, principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Execution was not found"));
    }

    private DelegatorView delegatorOf(ExecutionEntity execution) {
        var row = principals.findById(execution.getPrincipalId()).orElseThrow();
        return new DelegatorView(row.getId(), row.getKind());
    }

    private static StepView toStepView(ExecutionStepEntity row) {
        return new StepView(
                row.getSeq(),
                row.getType(),
                row.getToolName(),
                row.getDecision(),
                row.getReasonCode(),
                row.getArgsHmac(),
                row.getResultHmac(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getSpanId());
    }

    /** Null until the execution is terminal. */
    private static EvaluationView evaluation(ExecutionEntity execution) {
        if (execution.getVerdict() == null || execution.getSuccessful() == null) {
            return null;
        }
        return new EvaluationView(execution.getVerdict(), execution.getSuccessful());
    }

    /** Sums the stored steps into the reconstruction totals. */
    private static final class Totals {

        private int modelCalls;
        private int toolCallsRequested;
        private int toolCallsPermitted;
        private int toolCallsDenied;
        private int inputTokens;
        private int outputTokens;
        private long costMicros;

        static TotalsView of(List<ExecutionStepEntity> rows) {
            Totals totals = new Totals();
            rows.forEach(totals::add);
            return new TotalsView(
                    totals.modelCalls,
                    totals.toolCallsRequested,
                    totals.toolCallsPermitted,
                    totals.toolCallsDenied,
                    totals.inputTokens,
                    totals.outputTokens,
                    totals.costMicros);
        }

        private void add(ExecutionStepEntity row) {
            if (row.getType() == StepType.MODEL) {
                modelCalls++;
            } else {
                countToolDecision(row.getDecision());
            }
            inputTokens += valueOrZero(row.getInputTokens());
            outputTokens += valueOrZero(row.getOutputTokens());
            costMicros += row.getCostMicros() == null ? 0 : row.getCostMicros();
        }

        private void countToolDecision(Decision decision) {
            toolCallsRequested++;
            if (decision == Decision.PERMITTED) {
                toolCallsPermitted++;
            } else if (decision == Decision.DENIED) {
                toolCallsDenied++;
            }
        }

        private static int valueOrZero(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
