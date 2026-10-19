package dev.mytechprofile.aigateway.gateway.tools;

import java.util.UUID;

/**
 * What a tool knows about the run: the execution id and the tenant. Tools must scope every query by {@code tenantId}.
 */
public record ToolExecutionContext(UUID executionId, String tenantId) {}
