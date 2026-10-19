package dev.mytechprofile.aigateway.execution;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.aigateway.authority.AgentResolver;
import dev.mytechprofile.aigateway.authority.AuthorizationDecision;
import dev.mytechprofile.aigateway.authority.ToolAuthorizer;
import dev.mytechprofile.aigateway.budget.BudgetExhaustedException;
import dev.mytechprofile.aigateway.budget.BudgetGuard;
import dev.mytechprofile.aigateway.budget.BudgetPolicy;
import dev.mytechprofile.aigateway.budget.CostCalculator;
import dev.mytechprofile.aigateway.domain.Decision;
import dev.mytechprofile.aigateway.domain.ExecutionStatus;
import dev.mytechprofile.aigateway.domain.ReasonCode;
import dev.mytechprofile.aigateway.evaluation.EvaluationInput;
import dev.mytechprofile.aigateway.evaluation.ExecutionEvaluator;
import dev.mytechprofile.aigateway.model.ConversationMessage;
import dev.mytechprofile.aigateway.model.ModelGateway;
import dev.mytechprofile.aigateway.model.ModelTurn;
import dev.mytechprofile.aigateway.model.ProposedToolCall;
import dev.mytechprofile.aigateway.persistence.DelegationGrantEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionEntity;
import dev.mytechprofile.aigateway.persistence.ExecutionRepository;
import dev.mytechprofile.aigateway.persistence.ExecutionStepRepository;
import dev.mytechprofile.aigateway.persistence.PrincipalRepository;
import dev.mytechprofile.aigateway.persistence.RecommendationDraftRepository;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;
import dev.mytechprofile.aigateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.tools.ToolRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the model and tool loop. Every proposed tool call is authorized before it executes.
 */
@Service
public class ExecutionOrchestrator {

    static final int MAX_TURNS = 10;

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);

    private final ExecutionRepository executions;
    private final ExecutionStepRepository steps;
    private final PrincipalRepository principals;
    private final AgentResolver agents;
    private final RecommendationDraftRepository drafts;
    private final ModelGateway models;
    private final BudgetGuard budget;
    private final CostCalculator costs;
    private final ToolAuthorizer authorizer;
    private final ToolRegistry tools;
    private final ExecutionStepRecorder recorder;
    private final ExecutionEvaluator evaluator;
    private final JsonMapper json;
    private final Tracer tracer;
    private final Clock clock;

    public ExecutionOrchestrator(
            ExecutionRepository executions,
            ExecutionStepRepository steps,
            PrincipalRepository principals,
            AgentResolver agents,
            RecommendationDraftRepository drafts,
            ModelGateway models,
            BudgetGuard budget,
            CostCalculator costs,
            ToolAuthorizer authorizer,
            ToolRegistry tools,
            ExecutionStepRecorder recorder,
            ExecutionEvaluator evaluator,
            JsonMapper json,
            Tracer tracer,
            Clock clock) {
        this.executions = executions;
        this.steps = steps;
        this.principals = principals;
        this.agents = agents;
        this.drafts = drafts;
        this.models = models;
        this.budget = budget;
        this.costs = costs;
        this.authorizer = authorizer;
        this.tools = tools;
        this.recorder = recorder;
        this.evaluator = evaluator;
        this.json = json;
        this.tracer = tracer;
        this.clock = clock;
    }

    @Transactional
    public void run(java.util.UUID executionId) {
        ExecutionEntity execution = executions.findById(executionId).orElseThrow();
        Span root = tracer.spanBuilder("ai.execution")
                .setAttribute("agent.id", execution.getAgentId())
                .setAttribute("delegator.id", execution.getPrincipalId())
                .setAttribute("execution.id", execution.getId().toString())
                .startSpan();
        try (Scope ignored = root.makeCurrent()) {
            try {
                drive(execution);
            } catch (BudgetExhaustedException ex) {
                finish(execution, ExecutionStatus.BUDGET_EXHAUSTED, null);
            } catch (RuntimeException ex) {
                log.error("execution {} failed with {}", executionId, ex.getClass().getSimpleName());
                finish(execution, ExecutionStatus.FAILED, "UNEXPECTED");
            }
        } finally {
            root.end();
        }
    }

    private void drive(ExecutionEntity execution) {
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            return;
        }
        execution.setStatus(ExecutionStatus.RUNNING);
        var principalEntity = principals.findById(execution.getPrincipalId()).orElseThrow();
        GatewayPrincipal principal = new GatewayPrincipal(
                principalEntity.getId(),
                principalEntity.getKind(),
                principalEntity.getTenantId(),
                Set.copyOf(principalEntity.getScopes()));
        DelegationGrantEntity grant = agents.activeGrant(principal, execution.getAgentId())
                .orElseThrow(() -> new IllegalStateException("delegation missing"));
        BudgetPolicy policy = BudgetPolicy.from(grant);
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.user(
                "Evaluate candidate " + execution.getCandidateId()
                        + ". Recommend a status transition; do not execute it."));
        int seq = 0;
        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            int inputEstimate = models.estimatedInputTokens(messages);
            int outputEstimate = models.maxOutputTokens();
            long reservedCost = costs.costMicros(models.modelId(), inputEstimate, outputEstimate);
            budget.reserveModelCall(execution.getId(), policy, inputEstimate + outputEstimate, reservedCost);
            ModelTurn modelTurn = models.complete(execution.getCandidateId(), messages);
            long actualCost = costs.costMicros(models.modelId(), modelTurn.inputTokens(), modelTurn.outputTokens());
            budget.reconcileModelUsage(
                    execution.getId(),
                    inputEstimate + outputEstimate,
                    modelTurn.inputTokens() + modelTurn.outputTokens(),
                    reservedCost,
                    actualCost);
            seq++;
            recorder.modelStep(execution, seq, models.modelId(), modelTurn, actualCost);
            if (!modelTurn.hasToolCalls()) {
                finish(execution, ExecutionStatus.COMPLETED, null);
                return;
            }
            for (ProposedToolCall call : modelTurn.toolCalls()) {
                messages.add(ConversationMessage.assistantTool(call.id(), call.name(), call.argumentsJson()));
                try {
                    budget.reserveToolCall(execution.getId(), policy);
                } catch (BudgetExhaustedException ex) {
                    finish(execution, ExecutionStatus.BUDGET_EXHAUSTED, null);
                    return;
                }
                DelegationGrantEntity currentGrant = agents.activeGrant(principal, execution.getAgentId())
                        .orElse(null);
                AuthorizationDecision decision = authorizer.authorize(
                        currentGrant, principal, execution.getAgentId(), call.name(), call.argumentsJson());
                String result;
                if (decision.permitted()) {
                    result = tools.get(call.name())
                            .execute(json.readTree(call.argumentsJson()), new ToolExecutionContext(
                                    execution.getId(), execution.getTenantId()));
                    log.info("tool {} decision PERMITTED reason {}", call.name(), ReasonCode.PERMITTED);
                } else {
                    result = json.createObjectNode()
                            .put("denied", true)
                            .put("reason", decision.reason().name())
                            .toString();
                    log.info("tool {} decision DENIED reason {}", call.name(), decision.reason());
                }
                seq++;
                recorder.toolStep(
                        execution,
                        seq,
                        call.name(),
                        decision.permitted() ? Decision.PERMITTED : Decision.DENIED,
                        decision.reason(),
                        call.argumentsJson(),
                        result);
                messages.add(ConversationMessage.toolResult(call.id(), call.name(), result));
            }
        }
        finish(execution, ExecutionStatus.FAILED, "MAX_TURNS");
    }

    private void finish(ExecutionEntity execution, ExecutionStatus status, String failureReason) {
        execution.setStatus(status);
        execution.setFailureReason(failureReason);
        execution.setCompletedAt(clock.instant());
        boolean denied = steps.findByExecutionIdOrderBySeqAsc(execution.getId()).stream()
                .anyMatch(step -> step.getDecision() == Decision.DENIED);
        var draft = drafts.findByExecutionId(execution.getId());
        List<String> cited = draft.map(item -> List.copyOf(item.getCitedEvidenceIds())).orElse(List.of());
        List<String> retrieved = execution.getRetrievedEvidenceIds() == null
                ? List.of()
                : List.copyOf(execution.getRetrievedEvidenceIds());
        var result = evaluator.evaluate(new EvaluationInput(status, denied, draft.isPresent(), cited, retrieved));
        execution.setVerdict(result.verdict());
        execution.setSuccessful(result.successful());
        executions.save(execution);
        log.info("execution {} status {}", execution.getId(), status);
    }
}
