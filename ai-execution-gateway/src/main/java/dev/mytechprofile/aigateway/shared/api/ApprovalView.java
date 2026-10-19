package dev.mytechprofile.aigateway.shared.api;

import java.util.UUID;

/**
 * Result of an approval: the request is {@code APPROVED} and the candidate is in its new status.
 */
public record ApprovalView(UUID transitionRequestId, String status, String candidateStatus) {}
