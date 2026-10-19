package dev.mytechprofile.aigateway.shared.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.aigateway.recruiting.transition.TransitionApprovalService;
import dev.mytechprofile.aigateway.shared.security.CurrentPrincipal;

/**
 * The human step outside the agent loop: a user approves a transition the agent only requested.
 *
 * {@snippet lang=shell :
 * REQ=$(curl -s localhost:8080/api/v1/executions/$ID/transition-requests \
 *   -H "X-API-Key: $DEMO_RECRUITER_API_KEY" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["transitionRequestId"])')
 * curl -s -X POST localhost:8080/api/v1/transition-requests/$REQ/approve -H "X-API-Key: $DEMO_RECRUITER_API_KEY"
 * # {"transitionRequestId":"...","status":"APPROVED","candidateStatus":"INTERVIEW"}
 * }
 */
@RestController
@RequestMapping("/api/v1/transition-requests")
public class TransitionController {

    private final TransitionApprovalService approvals;

    public TransitionController(TransitionApprovalService approvals) {
        this.approvals = approvals;
    }

    /** Human approval of a pending transition. See {@code TransitionApprovalService#approve} for the error cases. */
    @PostMapping("/{id}/approve")
    public ApprovalView approve(@PathVariable UUID id) {
        return approvals.approve(CurrentPrincipal.get(), id);
    }
}
