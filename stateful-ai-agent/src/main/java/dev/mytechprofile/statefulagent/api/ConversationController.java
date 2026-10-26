package dev.mytechprofile.statefulagent.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.statefulagent.agent.AgentTurnService;
import dev.mytechprofile.statefulagent.approval.ApprovalLockService;
import dev.mytechprofile.statefulagent.approval.ApprovalStatus;
import dev.mytechprofile.statefulagent.approval.ApprovalWorkflow;
import dev.mytechprofile.statefulagent.approval.SubmittedDecision;
import dev.mytechprofile.statefulagent.approval.ToolApprovalEntity;
import dev.mytechprofile.statefulagent.evaluation.TurnEvaluator;
import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import dev.mytechprofile.statefulagent.session.AgentSessionService;
import dev.mytechprofile.statefulagent.session.SessionAccess;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

/**
 * Sends a turn and records the human decision on a paused tool call.
 *
 * <p><strong>When to use:</strong> after {@code POST /api/v1/sessions} returned {@code id}.
 * Messages are owner-only. A high-risk decision may be sent by a same-tenant approver.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * POST /api/v1/sessions/{id}/messages
 * Authorization: Basic alice:&lt;password&gt;
 *
 * { "text": "Move application APP-1 to stage TECH_SCREEN. Call advanceApplication." }
 *
 * // 202
 * { "status": "AWAITING_APPROVAL", "approval": { "id": "...", "risk": "MEDIUM",
 *   "calls": [ { "toolCallKey": "call_1", "toolName": "advanceApplication" } ] } }
 * }</pre>
 *
 * <p>A new message while an approval is still {@code PENDING} returns 409.
 * A missing {@code Idempotency-Key} on a decision returns 400.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class ConversationController {

    private final AgentSessionService sessions;
    private final AgentTurnService turns;
    private final ApprovalWorkflow workflow;
    private final ApprovalLockService locks;
    private final TurnEvaluator evaluator;
    private final SessionService sessionLog;
    private final AgentProperties properties;

    public ConversationController(AgentSessionService sessions, AgentTurnService turns, ApprovalWorkflow workflow,
            ApprovalLockService locks, TurnEvaluator evaluator, SessionService sessionLog, AgentProperties properties) {
        this.sessions = sessions;
        this.turns = turns;
        this.workflow = workflow;
        this.locks = locks;
        this.evaluator = evaluator;
        this.sessionLog = sessionLog;
        this.properties = properties;
    }

    /**
     * Runs one recruiter message. Returns 202 when a write tool is waiting,
     * and 200 when the turn finished, was auto-denied, or failed a business rule.
     *
     * <p>Alice asking to reject {@code APP-1} returns 200 {@code DENIED} because
     * she cannot invoke {@code rejectApplication}. A status question returns 200
     * {@code COMPLETED} with no approval.
     *
     * @param sessionId owned session
     * @param request recruiter text, at most 4000 characters
     * @return turn status, model answer, and the approval when one was opened
     */
    @PostMapping("/messages")
    ResponseEntity<TurnResult> message(@PathVariable UUID sessionId, @Valid @RequestBody SessionController.MessageRequest request) {
        AgentSessionEntity owned = this.sessions.requireOwned(sessionId.toString(), principal());
        TurnResult result = this.turns.message(owned, principal(), request.text());
        HttpStatus status = "AWAITING_APPROVAL".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Lists approvals for the session, optionally filtered by status.
     *
     * <p>{@code GET .../approvals?status=PENDING} is how a caller finds a pause
     * that is still waiting. The owner and a same-tenant approver can list them.
     * A different tenant receives 404.
     *
     * @param sessionId visible session
     * @param status omit to return every status
     * @return approval rows in creation order
     */
    @GetMapping("/approvals")
    List<TurnResult.ApprovalView> approvals(@PathVariable UUID sessionId, @RequestParam(required = false) ApprovalStatus status) {
        AgentSessionEntity visible = this.sessions.requireVisible(sessionId.toString(), principal());
        return this.locks.forSession(visible.getSessionId()).stream()
                .filter(approval -> status == null || approval.getStatus() == status)
                .map(this.workflow::viewOf)
                .toList();
    }

    /**
     * Allows or denies the paused tool calls. The same idempotency key returns
     * the stored result. A different key after the row is decided returns 409.
     *
     * <p>Alice allowing an above-band offer returns 403 and leaves the approval
     * {@code PENDING}. Bob posting the same body with a new {@code Idempotency-Key}
     * returns 200 {@code COMPLETED}.
     *
     * @param sessionId session that owns the approval
     * @param approvalId {@code approval.id} from the 202 response
     * @param idempotencyKey required header, unique per decision attempt
     * @param request one decision per paused call
     * @return {@code COMPLETED}, {@code DENIED}, or {@code FAILED}
     */
    @PostMapping("/approvals/{approvalId}/decision")
    TurnResult decide(@PathVariable UUID sessionId, @PathVariable UUID approvalId,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody DecisionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required.");
        }
        AgentSessionEntity visible = this.sessions.requireVisible(sessionId.toString(), principal());
        TurnResult result = this.workflow.decide(visible, principal(), approvalId, idempotencyKey, request.toDecisions());
        this.evaluator.evaluate(visible.getSessionId(), false);
        return result;
    }

    /**
     * Re-runs a {@code FAILED} approval with the decisions already stored on it.
     *
     * <p>Retrying a scorecard that failed because the failure gate fired inserts
     * the scorecard once. Retrying a {@code PENDING} or {@code COMPLETED} row
     * returns 409.
     *
     * @param sessionId session that owns the approval
     * @param approvalId failed approval
     * @return the new turn result
     */
    @PostMapping("/approvals/{approvalId}/retry")
    TurnResult retry(@PathVariable UUID sessionId, @PathVariable UUID approvalId) {
        AgentSessionEntity visible = this.sessions.requireVisible(sessionId.toString(), principal());
        TurnResult result = this.workflow.retry(visible, principal(), approvalId);
        this.evaluator.evaluate(visible.getSessionId(), false);
        return result;
    }

    /**
     * Returns the session event log. Owner only.
     *
     * <p>{@code view=all} keeps compacted messages. {@code view=active} is the
     * window the model sees. Bob requesting Alice's events receives 404.
     *
     * @param sessionId owned session
     * @param view {@code active} by default, or {@code all}
     * @return events with type, text, and message parts
     */
    @GetMapping("/events")
    List<EventView> events(@PathVariable UUID sessionId, @RequestParam(defaultValue = "active") String view) {
        this.sessions.requireOwned(sessionId.toString(), principal());
        EventFilter filter = "all".equals(view) ? EventFilter.all() : EventFilter.active();
        return SessionAccess.callAsSystem(() -> this.sessionLog.getEvents(sessionId.toString(), filter)).stream()
                .map(EventView::from)
                .toList();
    }

    /**
     * Returns the latest post-turn evaluation for the session owner.
     *
     * <p>After an allow, {@code passed} is {@code true} when the hire has an
     * accepted offer and gated advances have a passing scorecard. A session
     * with no evaluation event returns 404.
     *
     * @param sessionId owned session
     * @return {@code passed} and the comma-separated checks
     */
    @GetMapping("/evaluation")
    EvaluationView evaluation(@PathVariable UUID sessionId) {
        this.sessions.requireOwned(sessionId.toString(), principal());
        SessionEvent event = this.evaluator.latest(sessionId.toString());
        if (event == null) {
            throw new SessionNotFoundException();
        }
        return new EvaluationView(String.valueOf(event.getPayload().get("passed")), String.valueOf(event.getPayload().get("checks")));
    }

    private AgentPrincipal principal() {
        return AgentPrincipal.current(this.properties);
    }

    /** Allow or deny body. One entry per {@code calls[]} item from the pause. */
    public record DecisionRequest(@NotEmpty List<CallDecision> decisions) {
        List<SubmittedDecision> toDecisions() {
            return this.decisions.stream()
                    .map(decision -> new SubmittedDecision(decision.toolCallKey(), decision.decision() == Choice.ALLOW, decision.reason()))
                    .toList();
        }
    }

    /**
     * One tool-call decision. {@code toolCallKey} is {@code calls[0].toolCallKey}.
     * {@code reason} is stored when {@code decision} is {@code DENY}.
     */
    public record CallDecision(@NotBlank String toolCallKey, @NotNull Choice decision, String reason) {
    }

    /** Decision value in the request body. */
    public enum Choice {
        ALLOW,
        DENY
    }

    /** One session event. {@code type} is the framework event name, such as a compaction event. */
    public record EventView(String id, String type, String text, List<PartView> parts) {
        static EventView from(SessionEvent event) {
            List<PartView> parts = List.of();
            String text = null;
            if (event.hasMessage() && event.getMessage() instanceof AbstractMessage message) {
                text = message.getText();
                parts = message.getParts().stream().map(PartView::from).toList();
            }
            return new EventView(event.getId(), event.getType().name(), text, parts);
        }
    }

    /** A message part. {@code payloadProvider} is set when the part came from another model provider. */
    public record PartView(String partType, String text, String payloadProvider) {
        static PartView from(MessagePart part) {
            String provider = part.payload() == null ? null : part.payload().provider();
            String text = part instanceof org.springframework.ai.chat.messages.part.TextPart textPart ? textPart.text() : null;
            return new PartView(part.getClass().getSimpleName(), text, provider);
        }
    }

    /** Latest evaluation payload. {@code checks} is a comma-separated list such as {@code view=ok,hired=ok}. */
    public record EvaluationView(String passed, String checks) {
    }
}
