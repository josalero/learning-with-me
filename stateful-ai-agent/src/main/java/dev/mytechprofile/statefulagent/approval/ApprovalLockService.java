package dev.mytechprofile.statefulagent.approval;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.statefulagent.platform.AgentPrincipal;
import dev.mytechprofile.statefulagent.platform.ApprovalConflictException;
import dev.mytechprofile.statefulagent.platform.SessionNotFoundException;
import dev.mytechprofile.statefulagent.session.AgentSessionEntity;
import dev.mytechprofile.statefulagent.tools.RiskLevel;
import dev.mytechprofile.statefulagent.tools.ToolAuthorizationService;

@Service
public class ApprovalLockService {

    private final ToolApprovalRepository approvals;
    private final ToolCallDecisionRepository decisions;
    private final ToolAuthorizationService authorization;
    private final Clock clock;

    public ApprovalLockService(ToolApprovalRepository approvals, ToolCallDecisionRepository decisions,
            ToolAuthorizationService authorization, Clock clock) {
        this.approvals = approvals;
        this.decisions = decisions;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public ToolApprovalEntity create(AgentSessionEntity session, AgentPrincipal principal, String eventId, RiskLevel risk,
            String traceId, String spanId, List<ToolCallDecisionEntity> calls) {
        Instant now = this.clock.instant();
        ToolApprovalEntity approval = new ToolApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setSessionId(session.getSessionId());
        approval.setTenantId(session.getTenantId());
        approval.setRequestedBy(principal.username());
        approval.setPausedReplyEventId(eventId);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setRiskLevel(risk.name());
        approval.setTraceId(traceId);
        approval.setSpanId(spanId);
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        ToolApprovalEntity saved = this.approvals.save(approval);
        calls.forEach(call -> {
            call.setApprovalId(saved.getId());
            this.decisions.save(call);
        });
        return saved;
    }

    @Transactional
    public BeginResult begin(UUID approvalId, AgentSessionEntity session, AgentPrincipal principal, String idempotencyKey,
            List<SubmittedDecision> submitted) {
        ToolApprovalEntity approval = this.approvals.lockById(approvalId).orElseThrow(SessionNotFoundException::new);
        if (!approval.getSessionId().equals(session.getSessionId())) {
            throw new SessionNotFoundException();
        }
        this.authorization.assertMayDecide(principal, session, RiskLevel.valueOf(approval.getRiskLevel()));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            if (idempotencyKey.equals(approval.getIdempotencyKey())) {
                return BeginResult.replay(approval);
            }
            throw new ApprovalConflictException("This approval was already decided with a different idempotency key.");
        }
        List<ToolCallDecisionEntity> stored = this.decisions.findByApprovalId(approval.getId());
        if (submitted.size() != stored.size()) {
            throw new ApprovalConflictException("A decision is required for every paused tool call.");
        }
        for (ToolCallDecisionEntity call : stored) {
            SubmittedDecision match = submitted.stream()
                    .filter(item -> item.toolCallKey().equals(call.getToolCallKey()))
                    .findFirst()
                    .orElseThrow(() -> new ApprovalConflictException("A decision is missing for a paused tool call."));
            call.setDecision(match.allowed() ? "ALLOW" : "DENY");
            call.setDenyMessage(match.allowed() ? null : sanitize(match.reason()));
        }
        boolean allDenied = stored.stream().allMatch(call -> "DENY".equals(call.getDecision()));
        Instant now = this.clock.instant();
        approval.setStatus(ApprovalStatus.EXECUTING);
        approval.setIdempotencyKey(idempotencyKey);
        approval.setDecidedBy(principal.username());
        approval.setDecidedAt(now);
        approval.setUpdatedAt(now);
        return new BeginResult(false, allDenied, approval.getStatus(), null, approval.getPausedReplyEventId(), stored);
    }

    @Transactional
    public void finish(UUID approvalId, ApprovalStatus status, String answer, String errorCode) {
        ToolApprovalEntity approval = this.approvals.lockById(approvalId).orElseThrow(SessionNotFoundException::new);
        approval.setStatus(status);
        approval.setAnswer(answer);
        approval.setUpdatedAt(this.clock.instant());
        this.decisions.findByApprovalId(approvalId).forEach(call -> {
            call.setExecutionStatus(status.name());
            call.setErrorCode(errorCode);
        });
    }

    @Transactional
    public List<UUID> markStaleExecuting(Instant cutoff) {
        List<ToolApprovalEntity> stale = this.approvals.findByStatusAndUpdatedAtBefore(ApprovalStatus.EXECUTING, cutoff);
        stale.forEach(approval -> {
            approval.setStatus(ApprovalStatus.NEEDS_RECONCILIATION);
            approval.setUpdatedAt(this.clock.instant());
        });
        return stale.stream().map(ToolApprovalEntity::getId).toList();
    }

    @Transactional(readOnly = true)
    public List<ToolApprovalEntity> needingReconciliation() {
        return this.approvals.findByStatus(ApprovalStatus.NEEDS_RECONCILIATION);
    }

    public ToolApprovalEntity require(UUID id) {
        return this.approvals.findById(id).orElseThrow(SessionNotFoundException::new);
    }

    public List<ToolApprovalEntity> forSession(String sessionId) {
        return this.approvals.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public List<ToolApprovalEntity> pending(String sessionId) {
        return this.approvals.findBySessionIdAndStatus(sessionId, ApprovalStatus.PENDING);
    }

    public List<ToolCallDecisionEntity> calls(UUID approvalId) {
        return this.decisions.findByApprovalId(approvalId);
    }

    private static String sanitize(String reason) {
        String cleaned = reason == null ? "" : reason.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleaned.isBlank()) {
            return "The user denied this tool call.";
        }
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    public record BeginResult(boolean replay, boolean allDenied, ApprovalStatus status, String answer, String eventId,
            List<ToolCallDecisionEntity> calls) {

        static BeginResult replay(ToolApprovalEntity approval) {
            return new BeginResult(true, false, approval.getStatus(), approval.getAnswer(), approval.getPausedReplyEventId(), List.of());
        }
    }
}
