package dev.mytechprofile.gateway.audit;

import java.time.Instant;

import dev.mytechprofile.gateway.connector.AuthenticationType;

/**
 * Domain event published after a tool invocation completes (success or failure).
 *
 * <p>Does not carry argument bodies or response payloads — only a salted hash and
 * safe metadata for audit rows and metrics.
 */
public record ToolInvocationCompleted(
        String invocationId,
        String correlationId,
        String sessionId,
        String toolName,
        String connectionName,
        String connectorType,
        String subject,
        String tenantId,
        AuthenticationType authenticationType,
        String authorizationDecision,
        String approvalOutcome,
        String argumentHash,
        Instant startedAt,
        Instant completedAt,
        String resultCategory,
        int removedFieldCount,
        String errorReference) {}
