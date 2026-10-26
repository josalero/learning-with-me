package dev.mytechprofile.statefulagent.approval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

import dev.mytechprofile.statefulagent.agent.ModelProviderRegistry;
import dev.mytechprofile.statefulagent.api.TurnResult;
import dev.mytechprofile.statefulagent.business.HiringActionRepository;
import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.ScenarioNarration;
import dev.mytechprofile.statefulagent.platform.ApprovalConflictException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import dev.mytechprofile.statefulagent.session.ModelProvider;
import dev.mytechprofile.statefulagent.session.SessionAccess;
import dev.mytechprofile.statefulagent.tools.ArgumentFingerprint;
import dev.mytechprofile.statefulagent.tools.HiringTools;
import dev.mytechprofile.statefulagent.tools.ToolOutcomeSink;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies an allow or deny to a paused tool call and recovers rows left in
 * {@code EXECUTING}.
 *
 * <p><strong>When to use:</strong> after a 202. Replaying the same idempotency
 * key returns the stored turn and does not write a second hiring action.
 */
@Service
public class ApprovalWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ApprovalWorkflow.class);

    private final ApprovalLockService locks;
    private final SessionService sessions;
    private final ModelProviderRegistry models;
    private final HiringTools hiringTools;
    private final HiringActionRepository actions;
    private final ToolOutcomeSink outcomes;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;

    public ApprovalWorkflow(ApprovalLockService locks, SessionService sessions, ModelProviderRegistry models,
            HiringTools hiringTools, HiringActionRepository actions, ToolOutcomeSink outcomes, JsonMapper jsonMapper,
            Tracer tracer) {
        this.locks = locks;
        this.sessions = sessions;
        this.models = models;
        this.hiringTools = hiringTools;
        this.actions = actions;
        this.outcomes = outcomes;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
    }

    /**
     * Locks the approval and runs each allowed tool, or records a deny.
     *
     * <p>Alice deciding an above-band offer throws
     * {@link dev.mytechprofile.statefulagent.platform.ApprovalForbiddenException}.
     * A salary of {@code 90000} on {@code APP-1} returns {@code FAILED} after the
     * tool runs. The same key a second time returns the first result.
     *
     * @param session session that owns the approval
     * @param principal owner, or a same-tenant approver for high risk
     * @param approvalId {@code approval.id} from the pause
     * @param idempotencyKey caller-supplied key, required
     * @param submitted one decision per paused call
     * @return completed, denied, or failed
     */
    public TurnResult decide(AgentSessionEntity session, AgentPrincipal principal, UUID approvalId, String idempotencyKey,
            List<SubmittedDecision> submitted) {
        ToolApprovalEntity approval = this.locks.require(approvalId);
        if (!approval.getSessionId().equals(session.getSessionId())) {
            throw new dev.mytechprofile.statefulagent.platform.SessionNotFoundException();
        }
        SpanBuilder builder = this.tracer.spanBuilder("agent.approval.decide")
                .setAttribute("approval.id", approvalId.toString())
                .setAttribute("session.id", session.getSessionId())
                .setAttribute("tenant.id", session.getTenantId());
        SpanContext link = storedLink(approval.getTraceId(), approval.getSpanId());
        if (link.isValid()) {
            builder.addLink(link);
        }
        Span span = builder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ApprovalLockService.BeginResult begun = this.locks.begin(approvalId, session, principal, idempotencyKey, submitted);
            if (begun.replay()) {
                log.info("Approval {} is already {}. The same idempotency key returned that result.", approvalId, begun.status());
                return finished(approvalId, begun.status(), begun.answer(), approval.getRiskLevel());
            }
            String what = begun.calls().stream()
                    .map(call -> ScenarioNarration.action(call.getToolName(), call.getArgumentsJson(), this.jsonMapper))
                    .reduce((left, right) -> left + " and " + right)
                    .orElse("the paused action");
            if (begun.allDenied()) {
                log.info("{} denied {} on approval {}. Nothing was written.", principal.username(), what, approvalId);
            }
            else {
                log.info("{} allowed {} on approval {}. This is {}.", principal.username(), what, approvalId,
                        ScenarioNarration.risk(approval.getRiskLevel()));
            }
            return runTools(session, approvalId, begun, approval.getRiskLevel());
        }
        finally {
            span.end();
        }
    }

    /**
     * Records a system deny for a tool the requester cannot invoke.
     *
     * <p>Alice asking to reject APP-1 returns {@code DENIED} and HTTP 200.
     * The high-risk approver check is not applied, because the tool never
     * waits for Bob.
     *
     * @param approvalId pending approval created for this turn
     * @param answer explanation returned to the requester
     * @return a denied turn
     */
    public TurnResult denyUnauthorized(UUID approvalId, String answer) {
        this.locks.systemDeny(approvalId, answer);
        ToolApprovalEntity approval = this.locks.require(approvalId);
        return TurnResult.denied(answer, viewOf(approval));
    }

    /**
     * Moves a {@code FAILED} approval back to {@code PENDING} and decides it
     * again with the stored allow or deny. A row that is not failed throws
     * {@link dev.mytechprofile.statefulagent.platform.ApprovalConflictException}.
     *
     * @param session session that owns the approval
     * @param principal caller who may decide at the stored risk
     * @param approvalId failed approval
     * @return the retried turn
     */
    public TurnResult retry(AgentSessionEntity session, AgentPrincipal principal, UUID approvalId) {
        ToolApprovalEntity loaded = this.locks.require(approvalId);
        if (!loaded.getSessionId().equals(session.getSessionId())) {
            throw new dev.mytechprofile.statefulagent.platform.SessionNotFoundException();
        }
        if (loaded.getStatus() != ApprovalStatus.FAILED) {
            throw new ApprovalConflictException("Only a failed approval can be retried.");
        }
        List<SubmittedDecision> submitted = this.locks.calls(approvalId).stream()
                .map(call -> new SubmittedDecision(call.getToolCallKey(), "ALLOW".equals(call.getDecision()), call.getDenyMessage()))
                .toList();
        log.info("{} is retrying approval {}, which failed earlier.", principal.username(), approvalId);
        this.locks.finish(approvalId, ApprovalStatus.PENDING, null, null);
        return decide(session, principal, approvalId, loaded.getIdempotencyKey(), submitted);
    }

    /**
     * Closes approvals the recovery job marked for reconciliation.
     *
     * <p>If a {@code hiring_action} key starts with {@code sessionId:approvalId:},
     * the approval becomes {@code COMPLETED}. Otherwise it becomes {@code FAILED}
     * with {@code NOT_APPLIED}. Pending rows are left alone.
     */
    public void reconcile() {
        for (ToolApprovalEntity approval : this.locks.needingReconciliation()) {
            boolean applied = this.actions.existsWithKeyPrefix(approval.getSessionId() + ":" + approval.getId() + ":");
            String answer = applied ? "Recovered: the hiring action was already recorded." : "Recovered: the hiring action was not recorded.";
            ApprovalStatus recovered = applied ? ApprovalStatus.COMPLETED : ApprovalStatus.FAILED;
            this.locks.finish(approval.getId(), recovered, answer, applied ? null : "NOT_APPLIED");
            log.info(applied
                    ? "Approval {} was recovered as completed. The hiring action had already been saved."
                    : "Approval {} was recovered as failed. The hiring action was never saved.", approval.getId());
            audit(approval.getSessionId(), AgentEvents.RECOVERY, "approvalId", approval.getId().toString(), "applied", Boolean.toString(applied));
        }
    }

    private TurnResult runTools(AgentSessionEntity session, UUID approvalId, ApprovalLockService.BeginResult begun, String risk) {
        AssistantMessage reply = loadReply(session.getSessionId(), begun.eventId());
        verifyHashes(reply, begun.calls());
        Map<String, Object> context = executionContext(session, approvalId, reply);
        this.outcomes.start();
        try {
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(ToolCallbacks.from(this.hiringTools))
                    .toolContext(context)
                    .build();
            ChatResponse paused = new ChatResponse(List.of(new Generation(reply)));
            ToolCallDecision[] decisions = begun.calls().stream().map(this::toDecision).toArray(ToolCallDecision[]::new);
            ToolExecutionResult executed = this.models.toolCallingManager()
                    .executeToolCalls(new Prompt(List.of(), options), paused, ToolCallDecisions.of(decisions));
            String answer = continueModel(session, executed);
            boolean failed = this.outcomes.failures().stream().findFirst().isPresent();
            ApprovalStatus status = failed ? ApprovalStatus.FAILED : begun.allDenied() ? ApprovalStatus.DENIED : ApprovalStatus.COMPLETED;
            String code = failed ? this.outcomes.failures().get(0).code() : null;
            this.locks.finish(approvalId, status, answer, code);
            if (failed) {
                ToolOutcomeSink.Failure failure = this.outcomes.failures().get(0);
                log.warn("Approval {} did not change the application: {}", approvalId, failure.message());
            }
            else if (status == ApprovalStatus.COMPLETED) {
                log.info("Approval {} completed.", approvalId);
            }
            audit(session.getSessionId(), failed ? AgentEvents.TOOL_FAILED : AgentEvents.TOOL_EXECUTED, "approvalId", approvalId.toString());
            audit(session.getSessionId(), AgentEvents.APPROVAL_DECIDED, "status", status.name());
            return finished(approvalId, status, answer, risk);
        }
        catch (RuntimeException exception) {
            log.error("Approval {} stopped because the tool threw. It is marked failed.", approvalId, exception);
            this.locks.finish(approvalId, ApprovalStatus.FAILED, "The approved action could not be completed.", "TOOL_FAILED");
            audit(session.getSessionId(), AgentEvents.TOOL_FAILED, "approvalId", approvalId.toString());
            throw exception;
        }
        finally {
            this.outcomes.clear();
        }
    }

    private String continueModel(AgentSessionEntity session, ToolExecutionResult executed) {
        return SessionAccess.callAsSystem(() -> continueAsOwner(session, executed));
    }

    private String continueAsOwner(AgentSessionEntity session, ToolExecutionResult executed) {
        List<Message> history = executed.conversationHistory();
        Message last = history.get(history.size() - 1);
        ChatClientResponse response = this.models.client(ModelProvider.valueOf(session.getProvider()))
                .prompt()
                .messages(last)
                .advisors(SessionAdvisors.session(session.getSessionId()))
                .toolContext(Map.of(HiringTools.TENANT_ID, session.getTenantId(), HiringTools.SESSION_ID, session.getSessionId()))
                .call()
                .chatClientResponse();
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "Done.";
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null || text.isBlank() ? "Done." : text;
    }

    private AssistantMessage loadReply(String sessionId, String eventId) {
        return SessionAccess.callAsSystem(() -> this.sessions.getEvents(sessionId, EventFilter.all()).stream()
                .filter(event -> event.getId().equals(eventId))
                .map(SessionEvent::getMessage)
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Paused reply is missing from the session log")));
    }

    private void verifyHashes(AssistantMessage reply, List<ToolCallDecisionEntity> calls) {
        List<String> keys = ToolCallDecisions.toolCallKeys(reply);
        for (ToolCallDecisionEntity call : calls) {
            int index = keys.indexOf(call.getToolCallKey());
            if (index < 0) {
                throw new ApprovalConflictException("The paused reply does not match the approval record.");
            }
            AssistantMessage.ToolCall toolCall = reply.getToolCalls().get(index);
            String hash = ArgumentFingerprint.sha256(ArgumentFingerprint.canonical(toolCall.name(), toolCall.arguments(), this.jsonMapper));
            if (!hash.equals(call.getArgumentsHash())) {
                throw new ApprovalConflictException("The paused reply does not match the approval record.");
            }
        }
    }

    private Map<String, Object> executionContext(AgentSessionEntity session, UUID approvalId, AssistantMessage reply) {
        Map<String, String> callKeys = new HashMap<>();
        List<String> keys = ToolCallDecisions.toolCallKeys(reply);
        List<AssistantMessage.ToolCall> toolCalls = reply.getToolCalls();
        for (int i = 0; i < toolCalls.size(); i++) {
            AssistantMessage.ToolCall toolCall = toolCalls.get(i);
            callKeys.put(ArgumentFingerprint.sha256(ArgumentFingerprint.canonical(toolCall.name(), toolCall.arguments(), this.jsonMapper)), keys.get(i));
        }
        Map<String, Object> context = new HashMap<>();
        context.put(HiringTools.TENANT_ID, session.getTenantId());
        context.put(HiringTools.SESSION_ID, session.getSessionId());
        context.put(HiringTools.APPROVAL_ID, approvalId.toString());
        context.put(HiringTools.CALL_KEYS, callKeys);
        return context;
    }

    private ToolCallDecision toDecision(ToolCallDecisionEntity call) {
        return "ALLOW".equals(call.getDecision())
                ? ToolCallDecision.allow(call.getToolCallKey())
                : ToolCallDecision.deny(call.getToolCallKey(), call.getDenyMessage());
    }

    private TurnResult finished(UUID approvalId, ApprovalStatus status, String answer, String risk) {
        TurnResult.ApprovalView view = view(this.locks.calls(approvalId), approvalId, status.name(), risk);
        return switch (status) {
            case DENIED -> TurnResult.denied(answer, view);
            case FAILED -> TurnResult.failed(answer, view);
            default -> new TurnResult(status.name(), answer, view);
        };
    }

    public TurnResult.ApprovalView viewOf(ToolApprovalEntity approval) {
        return view(this.locks.calls(approval.getId()), approval.getId(), approval.getStatus().name(), approval.getRiskLevel());
    }

    private TurnResult.ApprovalView view(List<ToolCallDecisionEntity> calls, UUID id, String status, String risk) {
        List<TurnResult.CallView> rendered = new ArrayList<>();
        for (ToolCallDecisionEntity call : calls) {
            rendered.add(new TurnResult.CallView(call.getToolCallKey(), call.getToolName(), call.getDecision()));
        }
        return new TurnResult.ApprovalView(id, status, risk, rendered);
    }

    private SpanContext storedLink(String traceId, String spanId) {
        if (traceId == null || spanId == null || traceId.isBlank() || spanId.isBlank()) {
            return SpanContext.getInvalid();
        }
        return SpanContext.createFromRemoteParent(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    }

    public void audit(String sessionId, org.springframework.ai.session.EventType type, String... pairs) {
        SessionAccess.runAsSystem(() -> {
            SessionEvent.Builder builder = SessionEvent.builder().sessionId(sessionId).type(type);
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                builder.payload(pairs[i], pairs[i + 1]);
            }
            this.sessions.appendEvent(builder.build());
        });
    }
}
