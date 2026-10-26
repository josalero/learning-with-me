package dev.mytechprofile.statefulagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.stereotype.Service;

import dev.mytechprofile.statefulagent.api.TurnResult;
import dev.mytechprofile.statefulagent.approval.AgentEvents;
import dev.mytechprofile.statefulagent.approval.ApprovalLockService;
import dev.mytechprofile.statefulagent.evaluation.TurnEvaluator;
import dev.mytechprofile.statefulagent.approval.ApprovalWorkflow;
import dev.mytechprofile.statefulagent.approval.SubmittedDecision;
import dev.mytechprofile.statefulagent.approval.ToolApprovalEntity;
import dev.mytechprofile.statefulagent.approval.ToolCallDecisionEntity;
import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.ScenarioNarration;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.platform.ApprovalConflictException;
import dev.mytechprofile.statefulagent.platform.ProviderIncompatibleException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import dev.mytechprofile.statefulagent.session.CompatibilityMode;
import dev.mytechprofile.statefulagent.session.ModelProvider;
import dev.mytechprofile.statefulagent.session.PromptSanitization;
import dev.mytechprofile.statefulagent.tools.ArgumentFingerprint;
import dev.mytechprofile.statefulagent.tools.HiringTools;
import dev.mytechprofile.statefulagent.tools.RiskEvaluator;
import dev.mytechprofile.statefulagent.tools.RiskLevel;
import dev.mytechprofile.statefulagent.tools.ToolAuthorizationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs one recruiter message against the session's model and opens an approval
 * when a write tool is requested.
 *
 * <p><strong>When to use:</strong> from {@code POST /messages} only. The method
 * does not apply the hiring change. {@link dev.mytechprofile.statefulagent.approval.ApprovalWorkflow#decide}
 * does that after a person allows the call.
 *
 * <p><strong>Example:</strong> {@code message(session, alice, "status of APP-1")}
 * with the stub returns {@code COMPLETED} and writes no approval. The same call
 * with {@code "schedule tech screen for APP-1"} returns {@code AWAITING_APPROVAL}.
 */
@Service
public class AgentTurnService {

    private static final Logger log = LoggerFactory.getLogger(AgentTurnService.class);

    private final SessionService sessions;
    private final ModelProviderRegistry models;
    private final ProviderCompatibilityChecker compatibility;
    private final ApprovalLockService locks;
    private final ApprovalWorkflow workflow;
    private final RiskEvaluator riskEvaluator;
    private final ToolAuthorizationService authorization;
    private final AgentProperties properties;
    private final JsonMapper jsonMapper;

    private final TurnEvaluator evaluator;
    private final Tracer tracer;

    public AgentTurnService(SessionService sessions, ModelProviderRegistry models, ProviderCompatibilityChecker compatibility,
            ApprovalLockService locks, ApprovalWorkflow workflow, RiskEvaluator riskEvaluator, ToolAuthorizationService authorization,
            AgentProperties properties, JsonMapper jsonMapper, TurnEvaluator evaluator, Tracer tracer) {
        this.sessions = sessions;
        this.models = models;
        this.compatibility = compatibility;
        this.locks = locks;
        this.workflow = workflow;
        this.riskEvaluator = riskEvaluator;
        this.authorization = authorization;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.evaluator = evaluator;
        this.tracer = tracer;
    }

    /**
     * Appends the recruiter text, calls the model, and either finishes the turn
     * or stores a pending approval.
     *
     * <p>A message while another approval is {@code PENDING} throws
     * {@link dev.mytechprofile.statefulagent.platform.ApprovalConflictException}.
     * A tool the principal cannot invoke returns {@code DENIED} without a pause.
     * Foreign message parts in {@code REJECT} mode throw
     * {@link dev.mytechprofile.statefulagent.platform.ProviderIncompatibleException}.
     *
     * @param session owned session, including its provider
     * @param principal authenticated recruiter or approver
     * @param text message body, already validated as non-blank
     * @return completed, awaiting, denied, or failed
     */
    public TurnResult message(AgentSessionEntity session, AgentPrincipal principal, String text) {
        resolveAbandoned(session, principal);
        ModelProvider provider = ModelProvider.valueOf(session.getProvider());
        List<String> issues = this.compatibility.issues(this.sessions.getActiveMessages(session.getSessionId()), provider.wireName());
        if (!issues.isEmpty() && CompatibilityMode.REJECT.name().equals(session.getCompatibilityMode())) {
            log.warn("{} cannot continue on {}. The history contains parts from another provider.", session.getSessionId(), provider);
            throw new ProviderIncompatibleException(issues);
        }
        if (!issues.isEmpty()) {
            PromptSanitization.enable(provider.wireName());
        }
        Span span = this.tracer.spanBuilder("agent.turn")
                .setAttribute("session.id", session.getSessionId())
                .setAttribute("tenant.id", session.getTenantId())
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ChatClientResponse response = this.models.client(provider)
                    .prompt()
                    .user(text)
                    .advisors(SessionAdvisors.session(session.getSessionId()))
                    .toolContext(Map.of(HiringTools.TENANT_ID, session.getTenantId(), HiringTools.SESSION_ID, session.getSessionId()))
                    .call()
                    .chatClientResponse();
            ChatResponse chatResponse = response.chatResponse();
            TurnResult result = chatResponse != null && ToolCallDecisions.isAwaitingDecision(chatResponse)
                    ? pause(session, principal, chatResponse, response.context(), span)
                    : TurnResult.completed(textOf(chatResponse));
            this.evaluator.evaluate(session.getSessionId(), "AWAITING_APPROVAL".equals(result.status()));
            if ("COMPLETED".equals(result.status()) && result.approval() == null) {
                log.info("{} got an answer and no hiring tool ran.", principal.username());
            }
            return result;
        }
        finally {
            span.end();
            PromptSanitization.clear();
        }
    }

    private TurnResult pause(AgentSessionEntity session, AgentPrincipal principal, ChatResponse chatResponse,
            Map<String, Object> context, Span span) {
        AssistantMessage reply = chatResponse.getResult().getOutput();
        String eventId = responseEventId(context);
        List<String> keys = ToolCallDecisions.toolCallKeys(reply);
        List<ToolCallDecisionEntity> calls = new ArrayList<>();
        RiskLevel risk = RiskLevel.LOW;
        boolean blocked = false;
        for (int i = 0; i < reply.getToolCalls().size(); i++) {
            AssistantMessage.ToolCall toolCall = reply.getToolCalls().get(i);
            RiskLevel callRisk = this.riskEvaluator.evaluate(session.getTenantId(), toolCall.name(), toolCall.arguments());
            if (callRisk.atLeast(risk)) {
                risk = callRisk;
            }
            if (!this.authorization.mayInvoke(principal, toolCall.name(), toolCall.arguments())) {
                blocked = true;
            }
            ToolCallDecisionEntity call = new ToolCallDecisionEntity();
            call.setId(UUID.randomUUID());
            call.setToolCallKey(keys.get(i));
            call.setToolName(toolCall.name());
            call.setArgumentsJson(toolCall.arguments());
            call.setArgumentsHash(ArgumentFingerprint.sha256(ArgumentFingerprint.canonical(toolCall.name(), toolCall.arguments(), this.jsonMapper)));
            call.setDecision("PENDING");
            calls.add(call);
        }
        String traceId = span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : null;
        String spanId = span.getSpanContext().isValid() ? span.getSpanContext().getSpanId() : null;
        ToolApprovalEntity approval = this.locks.create(session, principal, eventId, risk, traceId, spanId, calls);
        String what = calls.stream()
                .map(call -> ScenarioNarration.action(call.getToolName(), call.getArgumentsJson(), this.jsonMapper))
                .reduce((left, right) -> left + " and " + right)
                .orElse("run a tool");
        this.workflow.audit(session.getSessionId(), AgentEvents.APPROVAL_REQUESTED, "approvalId", approval.getId().toString(), "risk", risk.name());
        if (blocked) {
            log.info("{} is not allowed to {}. Denied without asking anyone to approve.", principal.username(), what);
            String answer = "The requester is not allowed to run this tool.";
            return this.workflow.denyUnauthorized(approval.getId(), answer);
        }
        log.info("{} asked to {}. This is {}. Waiting on approval {}.", principal.username(), what, ScenarioNarration.risk(risk.name()),
                approval.getId());
        return TurnResult.awaiting(this.workflow.viewOf(approval));
    }

    private void resolveAbandoned(AgentSessionEntity session, AgentPrincipal principal) {
        List<ToolApprovalEntity> pending = this.locks.pending(session.getSessionId());
        if (pending.isEmpty()) {
            return;
        }
        if (this.properties.getApproval().getOnNewInput() == AgentProperties.NewInputPolicy.REJECT) {
            log.warn("{} sent another message while approval {} is still waiting. Decide that approval first.", principal.username(),
                    pending.get(0).getId());
            throw new ApprovalConflictException("An approval is still pending. Decide it before sending another message.");
        }
        for (ToolApprovalEntity approval : pending) {
            List<SubmittedDecision> denied = this.locks.calls(approval.getId()).stream()
                    .map(call -> new SubmittedDecision(call.getToolCallKey(), false, "The user moved on."))
                    .toList();
            this.workflow.decide(session, principal, approval.getId(), "auto-deny-" + approval.getId(), denied);
        }
    }

    private static String responseEventId(Map<String, Object> context) {
        Object raw = context.get(SessionMemoryAdvisor.RESPONSE_EVENT_IDS_CONTEXT_KEY);
        if (raw instanceof List<?> ids && !ids.isEmpty()) {
            return String.valueOf(ids.get(ids.size() - 1));
        }
        throw new IllegalStateException("The paused reply was not stored in the session log.");
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
