package dev.mytechprofile.aigateway.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mytechprofile.aigateway.recruiting.TransitionApprovalService;
import dev.mytechprofile.aigateway.security.CurrentPrincipal;

@RestController
@RequestMapping("/api/v1/transition-requests")
public class TransitionController {

    private final TransitionApprovalService approvals;

    public TransitionController(TransitionApprovalService approvals) {
        this.approvals = approvals;
    }

    @PostMapping("/{id}/approve")
    public ApprovalView approve(@PathVariable UUID id) {
        return approvals.approve(CurrentPrincipal.get(), id);
    }
}
