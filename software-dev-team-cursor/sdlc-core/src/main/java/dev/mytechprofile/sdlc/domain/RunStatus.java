package dev.mytechprofile.sdlc.domain;

/**
 * Lifecycle of one feature run.
 */
public enum RunStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    ESCALATED,
    FAILED
}
