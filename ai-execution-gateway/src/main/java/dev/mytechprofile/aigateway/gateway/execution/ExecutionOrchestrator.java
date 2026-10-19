package dev.mytechprofile.aigateway.gateway.execution;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.mytechprofile.aigateway.gateway.audit.ExecutionStepRecorder;
import dev.mytechprofile.aigateway.gateway.audit.ExecutionStepRepository;
import dev.mytechprofile.aigateway.gateway.authority.AuthorizationDecision;
import dev.mytechprofile.aigateway.gateway.authority.Decision;
import dev.mytechprofile.aigateway.gateway.authority.ToolAuthorizer;
import dev.mytechprofile.aigateway.gateway.budget.BudgetExhaustedException;
import dev.mytechprofile.aigateway.gateway.budget.BudgetGuard;
import dev.mytechprofile.aigateway.gateway.budget.BudgetPolicy;
import dev.mytechprofile.aigateway.gateway.budget.CostCalculator;
import dev.mytechprofile.aigateway.gateway.delegation.AgentResolver;
import dev.mytechprofile.aigateway.gateway.delegation.DelegationGrantEntity;
import dev.mytechprofile.aigateway.gateway.evaluation.EvaluationInput;
import dev.mytechprofile.aigateway.gateway.evaluation.ExecutionEvaluator;
import dev.mytechprofile.aigateway.gateway.model.ConversationMessage;
import dev.mytechprofile.aigateway.gateway.model.ModelGateway;
import dev.mytechprofile.aigateway.gateway.model.ModelTurn;
import dev.mytechprofile.aigateway.gateway.model.ProposedToolCall;
import dev.mytechprofile.aigateway.gateway.tools.ToolExecutionContext;
import dev.mytechprofile.aigateway.gateway.tools.ToolRegistry;
import dev.mytechprofile.aigateway.recruiting.recommendation.RecommendationDraftRepository;
import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;
import dev.mytechprofile.aigateway.shared.security.PrincipalRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the model and tool loop. Every proposed tool call is budget-checked and authorized before it executes.
 *
 * <p>Start reading at {@link #run}, then {@code runTurns} and {@code handleToolCall}. One turn, simplified:
 * {@snippet :
 * budget.reserveModelCall(id, policy, estimatedTokens, estimatedCost);  // may throw BudgetExhaustedException
 * ModelTurn turn = models.complete(candidateId, messages);
 * for (ProposedToolCall call : turn.toolCalls()) {
 *     budget.reserveToolCall(id, policy);                                 // denied calls spend budget too
 *     AuthorizationDecision decision = authorizer.authorize(grant, delegator, agentId, call.name(), call.argumentsJson());
 *     String result = decision.permitted()
 *             ? tools.get(call.name()).execute(arguments, context)
 *             : "{\"denied\":true,\"reason\":\"" + decision.reason() + "\"}";
 *     recorder.toolStep(execution, seq, call.name(), ..., call.argumentsJson(), result);
 *     messages.add(ConversationMessage.toolResult(call.id(), call.name(), result));
 * }
 * }
 * <p>The loop stops when the model asks for no tool ({@code COMPLETED}), a reservation fails
 * ({@code BUDGET_EXHAUSTED}), or after 10 turns ({@code FAILED}, {@code MAX_TURNS}). Authorization rules live in
 * {@link ToolAuthorizer}, budget rules in {@link BudgetGuard}, and the verdict in {@link ExecutionEvaluator}.
 */
@Service
public class ExecutionOrchestrator {

    static final int MAX_TURNS = 10;
    static final String FAILURE_MAX_TURNS = "MAX_TURNS";
    static final String FAILURE_UNEXPECTED = "UNEXPECTED";

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
    private final TransactionTemplate transactions;
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
            TransactionTemplate transactions,
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
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Runs one queued execution to a terminal status. Called on a worker thread by {@link ExecutionDispatcher}.
     *
     * <p>Three short transactions: mark RUNNING (so pollers see it), run the loop, and, only if the loop
     * threw, mark FAILED. A failed loop rolls back its own steps; crash recovery is out of scope.
     */
    public void run(UUID executionId) {
        if (!Boolean.TRUE.equals(transactions.execute(status -> markRunning(executionId)))) {
            return;
        }
        Span root = startExecutionSpan(executions.findById(executionId).orElseThrow());
        try (Scope ignored = root.makeCurrent()) {
            transactions.executeWithoutResult(status -> runToTerminalStatus(executionId));
        } catch (RuntimeException ex) {
            log.error("execution {} failed with {}", executionId, ex.getClass().getSimpleName());
            transactions.executeWithoutResult(status -> markUnexpectedFailure(executionId));
        } finally {
            root.end();
        }
    }

    private boolean markRunning(UUID executionId) {
        ExecutionEntity execution = executions.findById(executionId).orElseThrow();
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            return false;
        }
        execution.setStatus(ExecutionStatus.RUNNING);
        executions.save(execution);
        return true;
    }

    private Span startExecutionSpan(ExecutionEntity execution) {
        return tracer.spanBuilder("ai.execution")
                .setAttribute("agent.id", execution.getAgentId())
                .setAttribute("delegator.id", execution.getPrincipalId())
                .setAttribute("execution.id", execution.getId().toString())
                .startSpan();
    }

    private void runToTerminalStatus(UUID executionId) {
        ExecutionEntity execution = executions.findById(executionId).orElseThrow();
        try {
            if (runTurns(startRun(execution))) {
                finish(execution, ExecutionStatus.COMPLETED, null);
            } else {
                finish(execution, ExecutionStatus.FAILED, FAILURE_MAX_TURNS);
            }
        } catch (BudgetExhaustedException ex) {
            finish(execution, ExecutionStatus.BUDGET_EXHAUSTED, null);
        }
    }

    private void markUnexpectedFailure(UUID executionId) {
        finish(executions.findById(executionId).orElseThrow(), ExecutionStatus.FAILED, FAILURE_UNEXPECTED);
    }

    private ActiveRun startRun(ExecutionEntity execution) {
        GatewayPrincipal delegator = delegatorOf(execution);
        DelegationGrantEntity grant = agents.activeGrant(delegator, execution.getAgentId())
                .orElseThrow(() -> new IllegalStateException("delegation missing"));
        return new ActiveRun(execution, delegator, BudgetPolicy.from(grant), openingPrompt(execution));
    }

    private GatewayPrincipal delegatorOf(ExecutionEntity execution) {
        var row = principals.findById(execution.getPrincipalId()).orElseThrow();
        return new GatewayPrincipal(row.getId(), row.getKind(), row.getTenantId(), Set.copyOf(row.getScopes()));
    }

    private static List<ConversationMessage> openingPrompt(ExecutionEntity execution) {
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.user("Evaluate candidate " + execution.getCandidateId()
                + ". Recommend a status transition; do not execute it."));
        return messages;
    }

    /**
     * Alternates model turns and tool calls.
     *
     * @return true when the model stopped asking for tools, false when {@link #MAX_TURNS} ran out
     * @throws BudgetExhaustedException when a model or tool reservation would exceed the grant's budget
     */
    private boolean runTurns(ActiveRun run) {
        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            ModelTurn modelTurn = callModelWithinBudget(run);
            if (!modelTurn.hasToolCalls()) {
                return true;
            }
            modelTurn.toolCalls().forEach(call -> handleToolCall(run, call));
        }
        return false;
    }

    private ModelTurn callModelWithinBudget(ActiveRun run) {
        UUID executionId = run.execution.getId();
        int inputEstimate = models.estimatedInputTokens(run.messages);
        int reservedTokens = inputEstimate + models.maxOutputTokens();
        long reservedCost = costs.costMicros(models.modelId(), inputEstimate, models.maxOutputTokens());
        budget.reserveModelCall(executionId, run.policy, reservedTokens, reservedCost);

        ModelTurn turn = models.complete(run.execution.getCandidateId(), run.messages);

        long actualCost = costs.costMicros(models.modelId(), turn.inputTokens(), turn.outputTokens());
        budget.reconcileModelUsage(
                executionId, reservedTokens, turn.inputTokens() + turn.outputTokens(), reservedCost, actualCost);
        recorder.modelStep(run.execution, run.nextSeq(), models.modelId(), turn, actualCost);
        return turn;
    }

    private void handleToolCall(ActiveRun run, ProposedToolCall call) {
        run.messages.add(ConversationMessage.assistantTool(call.id(), call.name(), call.argumentsJson()));
        budget.reserveToolCall(run.execution.getId(), run.policy);
        AuthorizationDecision decision = authorize(run, call);
        String result = decision.permitted() ? executeTool(run, call) : deniedResult(decision);
        log.info("tool {} decision {} reason {}", call.name(), decisionOf(decision), decision.reason());
        recorder.toolStep(run.execution, run.nextSeq(), call.name(), decisionOf(decision), decision.reason(),
                call.argumentsJson(), result);
        run.messages.add(ConversationMessage.toolResult(call.id(), call.name(), result));
    }

    private AuthorizationDecision authorize(ActiveRun run, ProposedToolCall call) {
        String agentId = run.execution.getAgentId();
        // Re-read per call so a grant revoked mid-run denies the next tool call.
        DelegationGrantEntity currentGrant = agents.activeGrant(run.delegator, agentId).orElse(null);
        return authorizer.authorize(currentGrant, run.delegator, agentId, call.name(), call.argumentsJson());
    }

    private String executeTool(ActiveRun run, ProposedToolCall call) {
        var context = new ToolExecutionContext(run.execution.getId(), run.execution.getTenantId());
        return tools.get(call.name()).execute(json.readTree(call.argumentsJson()), context);
    }

    private String deniedResult(AuthorizationDecision decision) {
        return json.createObjectNode().put("denied", true).put("reason", decision.reason().name()).toString();
    }

    private static Decision decisionOf(AuthorizationDecision decision) {
        return decision.permitted() ? Decision.PERMITTED : Decision.DENIED;
    }

    private void finish(ExecutionEntity execution, ExecutionStatus status, String failureReason) {
        execution.setStatus(status);
        execution.setFailureReason(failureReason);
        execution.setCompletedAt(clock.instant());
        var result = evaluator.evaluate(evaluationInput(execution, status));
        execution.setVerdict(result.verdict());
        execution.setSuccessful(result.successful());
        executions.save(execution);
        log.info("execution {} status {}", execution.getId(), status);
    }

    private EvaluationInput evaluationInput(ExecutionEntity execution, ExecutionStatus status) {
        boolean anyDenied = steps.findByExecutionIdOrderBySeqAsc(execution.getId()).stream()
                .anyMatch(step -> step.getDecision() == Decision.DENIED);
        var draft = drafts.findByExecutionId(execution.getId());
        List<String> cited = draft.map(item -> List.copyOf(item.getCitedEvidenceIds())).orElse(List.of());
        List<String> retrieved = execution.getRetrievedEvidenceIds() == null
                ? List.of()
                : List.copyOf(execution.getRetrievedEvidenceIds());
        return new EvaluationInput(status, anyDenied, draft.isPresent(), cited, retrieved);
    }

    /** State of one execution while its loop runs. Lives only on the worker thread. */
    private static final class ActiveRun {

        private final ExecutionEntity execution;
        private final GatewayPrincipal delegator;
        private final BudgetPolicy policy;
        private final List<ConversationMessage> messages;
        private int seq;

        private ActiveRun(
                ExecutionEntity execution,
                GatewayPrincipal delegator,
                BudgetPolicy policy,
                List<ConversationMessage> messages) {
            this.execution = execution;
            this.delegator = delegator;
            this.policy = policy;
            this.messages = messages;
        }

        private int nextSeq() {
            return ++seq;
        }
    }
}
