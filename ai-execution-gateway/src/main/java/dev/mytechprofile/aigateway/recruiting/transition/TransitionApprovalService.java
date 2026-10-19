package dev.mytechprofile.aigateway.recruiting.transition;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.aigateway.recruiting.candidate.CandidateEntity;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStateMachine;
import dev.mytechprofile.aigateway.shared.api.ApprovalView;
import dev.mytechprofile.aigateway.shared.api.GatewayException;
import dev.mytechprofile.aigateway.shared.security.GatewayPrincipal;
import dev.mytechprofile.aigateway.shared.security.PrincipalKind;

/**
 * Applies a pending transition. Agents cannot call this; a user principal with {@code transition:approve} must.
 *
 * {@snippet :
 * ApprovalView result = approvals.approve(alice, transitionRequestId);
 * // result.status() == "APPROVED", result.candidateStatus() == "INTERVIEW"
 * }
 */
@Service
public class TransitionApprovalService {

    private static final String APPROVE_SCOPE = "transition:approve";

    private final TransitionRequestRepository requests;
    private final CandidateRepository candidates;
    private final CandidateStateMachine stateMachine;
    private final Clock clock;

    public TransitionApprovalService(
            TransitionRequestRepository requests,
            CandidateRepository candidates,
            CandidateStateMachine stateMachine,
            Clock clock) {
        this.requests = requests;
        this.candidates = candidates;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    /**
     * Moves the candidate to the requested status and records who approved it.
     *
     * <p>{@code 403} for agents, services, or users without {@code transition:approve}; {@code 404} for a
     * request or candidate in another tenant; {@code 409} when the request was already decided or the
     * candidate's status changed since the agent asked.
     */
    @Transactional
    public ApprovalView approve(GatewayPrincipal principal, UUID requestId) {
        requireApprover(principal);
        TransitionRequestEntity request = pendingRequest(principal, requestId);
        CandidateEntity candidate = candidateOf(principal, request);
        requireStillValid(candidate, request);
        candidate.setStatus(request.getToStatus());
        request.setStatus(TransitionRequestStatus.APPROVED);
        request.setDecidedBy(principal.id());
        request.setDecidedAt(clock.instant());
        return new ApprovalView(request.getId(), request.getStatus().name(), candidate.getStatus().name());
    }

    private static void requireApprover(GatewayPrincipal principal) {
        if (principal.kind() != PrincipalKind.USER || !principal.scopes().contains(APPROVE_SCOPE)) {
            throw new GatewayException(HttpStatus.FORBIDDEN, "Approval is not permitted for this principal");
        }
    }

    private TransitionRequestEntity pendingRequest(GatewayPrincipal principal, UUID requestId) {
        TransitionRequestEntity request = requests
                .findByIdAndTenantId(requestId, principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Transition request was not found"));
        if (request.getStatus() != TransitionRequestStatus.PENDING) {
            throw new GatewayException(HttpStatus.CONFLICT, "Transition request is not pending");
        }
        return request;
    }

    private CandidateEntity candidateOf(GatewayPrincipal principal, TransitionRequestEntity request) {
        return candidates
                .findByIdAndTenantId(request.getCandidateId(), principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Candidate was not found"));
    }

    /** Someone may have moved the candidate between the agent's request and this approval. */
    private void requireStillValid(CandidateEntity candidate, TransitionRequestEntity request) {
        if (candidate.getStatus() != request.getFromStatus()
                || !stateMachine.allows(request.getFromStatus(), request.getToStatus())) {
            throw new GatewayException(HttpStatus.CONFLICT, "Transition is no longer valid");
        }
    }
}
