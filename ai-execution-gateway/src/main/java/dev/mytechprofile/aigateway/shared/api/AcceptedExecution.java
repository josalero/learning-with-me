package dev.mytechprofile.aigateway.shared.api;

import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;

/**
 * Body of the {@code 202} returned by {@code POST /api/v1/executions}. Poll the execution until its status is terminal.
 *
 * {@snippet lang=json :
 * {"executionId":"6f1c...","traceId":"4bf92f3577b34da6a3ce929d0e0e4736","status":"QUEUED"}
 * }
 */
public record AcceptedExecution(UUID executionId, String traceId, ExecutionStatus status) {}
