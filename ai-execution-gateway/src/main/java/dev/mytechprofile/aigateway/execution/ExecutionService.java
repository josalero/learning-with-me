package dev.mytechprofile.aigateway.execution;

import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.mytechprofile.aigateway.api.CreateExecutionRequest;
import dev.mytechprofile.aigateway.api.GatewayException;
import dev.mytechprofile.aigateway.authority.AgentResolver;
import dev.mytechprofile.aigateway.domain.ExecutionStatus;
import dev.mytechprofile.aigateway.domain.Verdict;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.ExecutionEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionRepository;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;
import io.opentelemetry.context.Context;

@Service
public class ExecutionService {

    public static final String TASK_TYPE = "CANDIDATE_EVALUATION";

    private final AgentResolver agents;
    private final CandidateRepository candidates;
    private final ExecutionRepository executions;
    private final ExecutionDispatcher dispatcher;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ExecutionService(
            AgentResolver agents,
            CandidateRepository candidates,
            ExecutionRepository executions,
            ExecutionDispatcher dispatcher,
            TransactionTemplate transactions,
            Clock clock) {
        this.agents = agents;
        this.candidates = candidates;
        this.executions = executions;
        this.dispatcher = dispatcher;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ExecutionEntity accept(GatewayPrincipal principal, CreateExecutionRequest request, String traceId, Context context) {
        if (!TASK_TYPE.equals(request.taskType())) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "Task type is not supported");
        }
        if (!agents.agentExists(request.agentId())) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "Agent was not found");
        }
        if (agents.activeGrant(principal, request.agentId()).isEmpty()) {
            throw new GatewayException(HttpStatus.FORBIDDEN, "No active delegation for the requested agent");
        }
        var candidate = candidates
                .findByIdAndTenantId(request.candidateId(), principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Candidate was not found"));
        if (!candidate.getRequisitionId().equals(request.requisitionId())) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "Requisition does not match the candidate");
        }
        if (!dispatcher.hasCapacity()) {
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "Execution dispatcher is full");
        }
        ExecutionEntity saved = transactions.execute(status -> {
            ExecutionEntity execution = new ExecutionEntity();
            execution.setId(UUID.randomUUID());
            execution.setTenantId(principal.tenantId());
            execution.setAgentId(request.agentId());
            execution.setPrincipalId(principal.id());
            execution.setTaskType(request.taskType());
            execution.setCandidateId(request.candidateId());
            execution.setStatus(ExecutionStatus.QUEUED);
            execution.setTraceId(traceId);
            execution.setRetrievedEvidenceIds(new ArrayList<>());
            execution.setCreatedAt(clock.instant());
            return executions.saveAndFlush(execution);
        });
        try {
            dispatcher.dispatch(saved.getId(), context);
        } catch (RejectedExecutionException ex) {
            transactions.executeWithoutResult(status -> markRejected(saved.getId()));
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "Execution dispatcher is full");
        }
        return saved;
    }

    private void markRejected(UUID executionId) {
        ExecutionEntity execution = executions.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFailureReason("DISPATCH_REJECTED");
        execution.setVerdict(Verdict.NOT_EVALUATED);
        execution.setSuccessful(false);
        execution.setCompletedAt(clock.instant());
        executions.save(execution);
    }
}
