package dev.mytechprofile.aigateway.domain;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    BUDGET_EXHAUSTED,
    FAILED
}
