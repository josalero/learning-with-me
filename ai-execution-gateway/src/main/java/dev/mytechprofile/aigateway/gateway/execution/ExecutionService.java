package dev.mytechprofile.aigateway.gateway.execution;

import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.mytechprofile.aigateway.gateway.delegation.AgentResolver;
import dev.mytechprofile.aigateway.gateway.evaluation.Verdict;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.shared.api.CreateExecutionRequest;
import dev.mytechprofile.aigateway.shared.api.GatewayException;
import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;
import io.opentelemetry.context.Context;

/**
 * Accepts an execution request on the request thread: validates it, saves the {@code QUEUED} row,
 * and hands it to {@link ExecutionDispatcher}.
 *
 * {@snippet :
 * ExecutionEntity saved = executions.accept(CurrentPrincipal.get(), request, traceId, Context.current());
 * // saved.getStatus() == QUEUED; the worker marks it RUNNING, then a terminal status
 * }
 */
@Service
public class ExecutionService {

    public static final String TASK_TYPE = "CANDIDATE_EVALUATION";
    static final String FAILURE_DISPATCH_REJECTED = "DISPATCH_REJECTED";

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

    /**
     * Validates, persists, and starts an execution. The work itself runs later on the dispatcher pool.
     *
     * @throws GatewayException {@code 400} unsupported task or requisition mismatch, {@code 403} no active
     *     delegation, {@code 404} unknown agent or candidate outside the tenant, {@code 503} pool full
     */
    public ExecutionEntity accept(GatewayPrincipal principal, CreateExecutionRequest request, String traceId, Context context) {
        validate(principal, request);
        if (!dispatcher.hasCapacity()) {
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "Execution dispatcher is full");
        }
        ExecutionEntity saved = transactions.execute(status -> executions.saveAndFlush(queued(principal, request, traceId)));
        try {
            dispatcher.dispatch(saved.getId(), context);
        } catch (RejectedExecutionException ex) {
            // hasCapacity is a hint; another request can take the last slot between the check and dispatch.
            transactions.executeWithoutResult(status -> markRejected(saved.getId()));
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "Execution dispatcher is full");
        }
        return saved;
    }

    private void validate(GatewayPrincipal principal, CreateExecutionRequest request) {
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
    }

    private ExecutionEntity queued(GatewayPrincipal principal, CreateExecutionRequest request, String traceId) {
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
        return execution;
    }

    private void markRejected(UUID executionId) {
        ExecutionEntity execution = executions.findById(executionId).orElseThrow();
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setFailureReason(FAILURE_DISPATCH_REJECTED);
        execution.setVerdict(Verdict.NOT_EVALUATED);
        execution.setSuccessful(false);
        execution.setCompletedAt(clock.instant());
        executions.save(execution);
    }
}
