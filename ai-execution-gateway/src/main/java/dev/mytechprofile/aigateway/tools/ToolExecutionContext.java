package dev.mytechprofile.aigateway.tools;

import java.util.UUID;

public record ToolExecutionContext(UUID executionId, String tenantId) {}
