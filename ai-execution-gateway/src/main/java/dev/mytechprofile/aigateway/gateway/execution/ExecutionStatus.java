package dev.mytechprofile.aigateway.gateway.execution;

/**
 * Where an execution is. {@code COMPLETED} only means the loop ended; read the verdict for whether the task was good.
 */
public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    BUDGET_EXHAUSTED,
    FAILED
}
