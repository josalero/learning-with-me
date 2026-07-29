package dev.mytechprofile.gateway.model;

import java.time.Duration;

/**
 * Stable gateway errors returned to MCP clients.
 *
 * <p>Never expose stack traces, tokens, or raw downstream bodies.
 */
public sealed interface GatewayError {

    String type();

    String message();

    record ValidationError(String field, String detail) implements GatewayError {
        @Override
        public String type() {
            return "validation_error";
        }

        @Override
        public String message() {
            return field + ": " + detail;
        }
    }

    record AccessDenied(String reason) implements GatewayError {
        @Override
        public String type() {
            return "access_denied";
        }

        @Override
        public String message() {
            return reason;
        }
    }

    record QuotaExceeded(Duration retryAfter) implements GatewayError {
        @Override
        public String type() {
            return "quota_exceeded";
        }

        @Override
        public String message() {
            long seconds = Math.max(1, retryAfter == null ? 1 : retryAfter.toSeconds());
            return "quota exceeded; retry after " + seconds + "s";
        }
    }

    record Timeout(String reference, String detail) implements GatewayError {
        @Override
        public String type() {
            return "timeout";
        }

        @Override
        public String message() {
            return reference + ": " + detail;
        }
    }

    record TemporaryUnavailable(String reference, String detail) implements GatewayError {
        @Override
        public String type() {
            return "temporary_unavailable";
        }

        @Override
        public String message() {
            return reference + ": " + detail;
        }
    }

    record PermanentConnectorFailure(String reference, String detail) implements GatewayError {
        @Override
        public String type() {
            return "connector_failure";
        }

        @Override
        public String message() {
            return reference + ": " + detail;
        }
    }

    record ConfigurationFailure(String tool, String reason) implements GatewayError {
        @Override
        public String type() {
            return "configuration_failure";
        }

        @Override
        public String message() {
            return tool + ": " + reason;
        }
    }
}
