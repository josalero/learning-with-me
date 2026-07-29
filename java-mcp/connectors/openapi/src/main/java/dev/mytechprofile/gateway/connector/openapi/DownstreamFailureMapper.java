package dev.mytechprofile.gateway.connector.openapi;

import dev.mytechprofile.gateway.connector.ExecutionResult;

/**
 * Maps downstream HTTP and transport failures to stable {@link ExecutionResult.Failure} kinds.
 */
final class DownstreamFailureMapper {

    private DownstreamFailureMapper() {}

    static ExecutionResult fromHttp(String operationId, int statusCode, String statusText) {
        if (statusCode == 401 || statusCode == 403) {
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.UNAUTHORIZED, operationId, "downstream unauthorized");
        }
        if (statusCode == 503 || statusCode == 429) {
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.TEMPORARY, operationId, "downstream temporary failure");
        }
        if (statusCode >= 500) {
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.PERMANENT, operationId, "downstream permanent failure");
        }
        String detail = statusText == null || statusText.isBlank() ? "HTTP " + statusCode : statusText;
        return new ExecutionResult.Failure(ExecutionResult.FailureKind.PERMANENT, operationId, detail);
    }

    static ExecutionResult fromTransport(String operationId, String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        if (normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("read timed out")) {
            return new ExecutionResult.Failure(
                    ExecutionResult.FailureKind.TIMEOUT, operationId, "downstream timed out");
        }
        return new ExecutionResult.Failure(
                ExecutionResult.FailureKind.TEMPORARY, operationId, "downstream call failed");
    }
}
