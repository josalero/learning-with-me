package dev.mytechprofile.aigateway.execution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.aigateway.api.DelegatorView;
import dev.mytechprofile.aigateway.api.EvaluationView;
import dev.mytechprofile.aigateway.api.ExecutionView;
import dev.mytechprofile.aigateway.api.GatewayException;
import dev.mytechprofile.aigateway.api.ReconstructionView;
import dev.mytechprofile.aigateway.api.StepView;
import dev.mytechprofile.aigateway.api.TotalsView;
import dev.mytechprofile.aigateway.api.TransitionRequestView;
import dev.mytechprofile.aigateway.domain.Decision;
import dev.mytechprofile.aigateway.domain.StepType;
import dev.mytechprofile.aigateway.persistence.ExecutionEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionRepository;
import dev.mytechprofile.aigateway.persistence.ExecutionStepEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionStepRepository;
import dev.mytechprofile.aigateway.persistence.PrincipalRepository;
import dev.mytechprofile.aigateway.persistence.TransitionRequestRepository;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;

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

    @Transactional(readOnly = true)
    public ReconstructionView reconstruct(GatewayPrincipal principal, UUID executionId) {
        ExecutionEntity execution = require(principal, executionId);
        var principalRow = principals.findById(execution.getPrincipalId()).orElseThrow();
        var rows = steps.findByExecutionIdOrderBySeqAsc(executionId);
        int modelCalls = 0;
        int requested = 0;
        int permitted = 0;
        int denied = 0;
        int input = 0;
        int output = 0;
        long cost = 0;
        var views = new java.util.ArrayList<StepView>();
        for (ExecutionStepEntity row : rows) {
            if (row.getType() == StepType.MODEL) {
                modelCalls++;
            } else {
                requested++;
                if (row.getDecision() == Decision.PERMITTED) {
                    permitted++;
                } else if (row.getDecision() == Decision.DENIED) {
                    denied++;
                }
            }
            input += row.getInputTokens() == null ? 0 : row.getInputTokens();
            output += row.getOutputTokens() == null ? 0 : row.getOutputTokens();
            cost += row.getCostMicros() == null ? 0 : row.getCostMicros();
            views.add(new StepView(
                    row.getSeq(),
                    row.getType(),
                    row.getToolName(),
                    row.getDecision(),
                    row.getReasonCode(),
                    row.getArgsHmac(),
                    row.getResultHmac(),
                    row.getInputTokens(),
                    row.getOutputTokens(),
                    row.getSpanId()));
        }
        return new ReconstructionView(
                execution.getId(),
                execution.getTraceId(),
                execution.getAgentId(),
                new DelegatorView(principalRow.getId(), principalRow.getKind()),
                execution.getStatus(),
                views,
                new TotalsView(modelCalls, requested, permitted, denied, input, output, cost),
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

    private static EvaluationView evaluation(ExecutionEntity execution) {
        if (execution.getVerdict() == null || execution.getSuccessful() == null) {
            return null;
        }
        return new EvaluationView(execution.getVerdict(), execution.getSuccessful());
    }
}
