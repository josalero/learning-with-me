package dev.mytechprofile.aigateway.recruiting;

import java.time.Clock;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.mytechprofile.aigateway.api.ApprovalView;
import dev.mytechprofile.aigateway.api.GatewayException;
import dev.mytechprofile.aigateway.domain.PrincipalKind;
import dev.mytechprofile.aigateway.domain.TransitionRequestStatus;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.persistence.TransitionRequestRepository;
import dev.mytechprofile.aigateway.security.GatewayPrincipal;

/**
 * Applies a pending transition. Agents cannot call this; a user principal with approval scope must.
 */
@Service
public class TransitionApprovalService {

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

    @Transactional
    public ApprovalView approve(GatewayPrincipal principal, UUID requestId) {
        if (principal.kind() != PrincipalKind.USER || !principal.scopes().contains("transition:approve")) {
            throw new GatewayException(HttpStatus.FORBIDDEN, "Approval is not permitted for this principal");
        }
        var request = requests
                .findByIdAndTenantId(requestId, principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Transition request was not found"));
        if (request.getStatus() != TransitionRequestStatus.PENDING) {
            throw new GatewayException(HttpStatus.CONFLICT, "Transition request is not pending");
        }
        var candidate = candidates
                .findByIdAndTenantId(request.getCandidateId(), principal.tenantId())
                .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND, "Candidate was not found"));
        if (candidate.getStatus() != request.getFromStatus()
                || !stateMachine.allows(request.getFromStatus(), request.getToStatus())) {
            throw new GatewayException(HttpStatus.CONFLICT, "Transition is no longer valid");
        }
        candidate.setStatus(request.getToStatus());
        request.setStatus(TransitionRequestStatus.APPROVED);
        request.setDecidedBy(principal.id());
        request.setDecidedAt(clock.instant());
        return new ApprovalView(request.getId(), request.getStatus().name(), candidate.getStatus().name());
    }
}
