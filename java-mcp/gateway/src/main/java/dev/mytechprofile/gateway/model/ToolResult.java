package dev.mytechprofile.gateway.model;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import dev.mytechprofile.gateway.connector.ExecutionResult;

/**
 * MCP-facing tool outcome after connector execution and governance.
 */
public record ToolResult(boolean success, JsonNode body, GatewayError error) {

    public static ToolResult of(ExecutionResult result) {
        return switch (result) {
            case ExecutionResult.Success success -> new ToolResult(true, success.payload(), null);
            case ExecutionResult.Failure failure -> new ToolResult(false, null, mapFailure(failure));
        };
    }

    public static ToolResult error(GatewayError error) {
        return new ToolResult(false, null, error);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (success) {
            map.put("ok", true);
            map.put("data", body);
            return map;
        }
        map.put("ok", false);
        map.put("error", error.type());
        map.put("message", error.message());
        if (error instanceof GatewayError.QuotaExceeded quota && quota.retryAfter() != null) {
            map.put("retryAfterSeconds", Math.max(1, quota.retryAfter().toSeconds()));
        }
        return map;
    }

    private static GatewayError mapFailure(ExecutionResult.Failure failure) {
        return switch (failure.kind()) {
            case TIMEOUT -> new GatewayError.Timeout(failure.reference(), failure.message());
            case TEMPORARY -> new GatewayError.TemporaryUnavailable(failure.reference(), failure.message());
            case UNAUTHORIZED -> new GatewayError.AccessDenied(failure.message());
            case NOT_FOUND, PERMANENT ->
                    new GatewayError.PermanentConnectorFailure(failure.reference(), failure.message());
        };
    }
}
