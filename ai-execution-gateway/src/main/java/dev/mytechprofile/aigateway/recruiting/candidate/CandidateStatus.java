package dev.mytechprofile.aigateway.recruiting.candidate;

/**
 * Pipeline status of a candidate. Only {@code TransitionApprovalService} changes it.
 */
public enum CandidateStatus {
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED
}
