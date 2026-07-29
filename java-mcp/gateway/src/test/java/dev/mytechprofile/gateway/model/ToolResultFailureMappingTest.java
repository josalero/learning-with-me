package dev.mytechprofile.gateway.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.connector.ExecutionResult;

class ToolResultFailureMappingTest {

    @Test
    void of_mapsTimeoutFailure() {
        ToolResult result = ToolResult.of(new ExecutionResult.Failure(
                ExecutionResult.FailureKind.TIMEOUT, "searchCandidates", "downstream timed out"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isInstanceOf(GatewayError.Timeout.class);
        assertThat(result.asMap()).containsEntry("error", "timeout");
    }

    @Test
    void of_mapsPermanentFailure() {
        ToolResult result = ToolResult.of(new ExecutionResult.Failure(
                ExecutionResult.FailureKind.PERMANENT, "searchCandidates", "downstream permanent failure"));
        assertThat(result.error()).isInstanceOf(GatewayError.PermanentConnectorFailure.class);
        assertThat(result.asMap()).containsEntry("error", "connector_failure");
    }

    @Test
    void of_mapsQuotaExceededWithRetryAfter() {
        ToolResult result = ToolResult.error(new GatewayError.QuotaExceeded(Duration.ofSeconds(42)));
        assertThat(result.asMap())
                .containsEntry("error", "quota_exceeded")
                .containsEntry("retryAfterSeconds", 42L);
    }
}
