package dev.mytechprofile.aigateway.shared.api;

import java.time.Instant;
import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;

/**
 * Polling view of one execution. {@code evaluation} is {@code null} until the status is terminal.
 */
public record ExecutionView(
        UUID executionId,
        ExecutionStatus status,
        String agentId,
        String delegatorId,
        EvaluationView evaluation,
        Instant createdAt,
        Instant completedAt) {}
