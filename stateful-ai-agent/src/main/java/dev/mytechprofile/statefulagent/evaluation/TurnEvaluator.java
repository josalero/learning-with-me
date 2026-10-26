package dev.mytechprofile.statefulagent.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Service;

import dev.mytechprofile.statefulagent.approval.AgentEvents;
import dev.mytechprofile.statefulagent.approval.ApprovalStatus;
import dev.mytechprofile.statefulagent.approval.ApprovalWorkflow;
import dev.mytechprofile.statefulagent.approval.ToolApprovalEntity;
import dev.mytechprofile.statefulagent.approval.ToolApprovalRepository;
import dev.mytechprofile.statefulagent.business.HiringActionRepository;
import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewRound;
import dev.mytechprofile.statefulagent.business.InterviewScorecardRepository;
import dev.mytechprofile.statefulagent.business.JobOfferRepository;
import dev.mytechprofile.statefulagent.business.StageTransitions;
import dev.mytechprofile.statefulagent.session.SessionAccess;

@Service
public class TurnEvaluator {

    private static final Logger log = LoggerFactory.getLogger(TurnEvaluator.class);

    private final SessionService sessions;
    private final ToolApprovalRepository approvals;
    private final HiringActionRepository actions;
    private final InterviewScorecardRepository scorecards;
    private final JobOfferRepository offers;
    private final ApprovalWorkflow workflow;

    public TurnEvaluator(SessionService sessions, ToolApprovalRepository approvals, HiringActionRepository actions,
            InterviewScorecardRepository scorecards, JobOfferRepository offers, ApprovalWorkflow workflow) {
        this.sessions = sessions;
        this.approvals = approvals;
        this.actions = actions;
        this.scorecards = scorecards;
        this.offers = offers;
        this.workflow = workflow;
    }

    public void evaluate(String sessionId, boolean awaiting) {
        try {
            List<String> checks = new ArrayList<>();
            checks.add(viewConsistent(sessionId) ? "view=ok" : "view=orphan-tool-call");
            checks.add(actionsCovered(sessionId) ? "actions=ok" : "actions=unapproved");
            checks.add(hiredHasAcceptedOffer(sessionId) ? "hired=ok" : "hired=missing-acceptance");
            checks.add(gatesHaveScorecards(sessionId) ? "gates=ok" : "gates=missing-scorecard");
            boolean passed = checks.stream().allMatch(check -> check.endsWith("=ok"));
            this.workflow.audit(sessionId, AgentEvents.EVALUATION, "passed", Boolean.toString(passed), "checks", String.join(",", checks),
                    "awaiting", Boolean.toString(awaiting));
        }
        catch (RuntimeException exception) {
            log.warn("Evaluation could not be recorded for session {}", sessionId);
        }
    }

    public SessionEvent latest(String sessionId) {
        return SessionAccess.callAsSystem(() -> this.sessions.getEvents(sessionId, EventFilter.all()).stream()
                .filter(event -> AgentEvents.EVALUATION.equals(event.getType()))
                .reduce((first, second) -> second)
                .orElse(null));
    }

    private boolean viewConsistent(String sessionId) {
        List<Message> messages = SessionAccess.callAsSystem(() -> this.sessions.getActiveMessages(sessionId));
        boolean open = this.approvals.findBySessionIdAndStatus(sessionId, ApprovalStatus.PENDING).stream().findAny().isPresent()
                || this.approvals.findBySessionIdAndStatus(sessionId, ApprovalStatus.EXECUTING).stream().findAny().isPresent();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                boolean answered = i + 1 < messages.size() && !(messages.get(i + 1) instanceof UserMessage);
                boolean last = i == messages.size() - 1;
                if (!answered && !(last && open)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean actionsCovered(String sessionId) {
        return this.actions.findAll().stream()
                .filter(action -> action.getIdempotencyKey().startsWith(sessionId + ":"))
                .allMatch(action -> approved(action.getIdempotencyKey()));
    }

    private boolean approved(String idempotencyKey) {
        String[] parts = idempotencyKey.split(":");
        if (parts.length < 2) {
            return false;
        }
        return this.approvals.findById(java.util.UUID.fromString(parts[1]))
                .filter(approval -> approval.getStatus() != ApprovalStatus.PENDING && approval.getStatus() != ApprovalStatus.DENIED)
                .isPresent();
    }

    private boolean hiredHasAcceptedOffer(String sessionId) {
        return this.actions.findAll().stream()
                .filter(action -> action.getIdempotencyKey().startsWith(sessionId + ":"))
                .filter(action -> action.getToStage() == HiringStage.HIRED)
                .allMatch(action -> this.offers.findByTenantIdAndApplicationId(action.getTenantId(), action.getApplicationId())
                        .filter(offer -> "ACCEPTED".equals(offer.getStatus()))
                        .isPresent());
    }

    private boolean gatesHaveScorecards(String sessionId) {
        return this.actions.findAll().stream()
                .filter(action -> action.getIdempotencyKey().startsWith(sessionId + ":"))
                .filter(action -> "ADVANCE".equals(action.getAction()))
                .allMatch(action -> {
                    InterviewRound round = StageTransitions.gatingRound(action.getToStage());
                    if (round == null) {
                        return true;
                    }
                    return this.scorecards.findFirstByTenantIdAndApplicationIdAndRoundOrderByCreatedAtDesc(
                            action.getTenantId(), action.getApplicationId(), round)
                            .filter(card -> card.getRecommendation().passing())
                            .isPresent();
                });
    }

    public List<ToolApprovalEntity> approvals(String sessionId) {
        return this.approvals.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}
