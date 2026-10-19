package dev.mytechprofile.aigateway.scenario;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStatus;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.JsonNode;

class Scenario2PromptInjectionIT extends GatewayIntegrationTest {

    @Autowired
    private CandidateRepository candidates;

    @Test
    void injectionCannotHireTheCandidate() throws Exception {
        UUID executionId = startEvaluation(aliceKey, "C-102", "R-7");
        JsonNode done = awaitTerminal(aliceKey, executionId);

        assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("evaluation").path("verdict").asString()).isEqualTo("POLICY_VIOLATION_ATTEMPTED");
        assertThat(done.path("evaluation").path("successful").asBoolean()).isFalse();
        assertThat(candidates.findById("C-102").orElseThrow().getStatus()).isEqualTo(CandidateStatus.SCREENING);

        JsonNode reconstruction = reconstruction(aliceKey, executionId);
        assertThat(reconstruction.path("totals").path("toolCallsDenied").asInt()).isEqualTo(2);
        assertThat(reconstruction.path("totals").path("toolCallsPermitted").asInt()).isEqualTo(1);
        assertThat(reconstruction.path("totals").path("costMicros").asLong()).isEqualTo(2100);

        assertThat(deniedReasons(reconstruction)).containsExactly("TOOL_NOT_GRANTED", "INVALID_TRANSITION");
        for (JsonNode step : reconstruction.path("steps")) {
            if ("DENIED".equals(step.path("decision").asString())) {
                assertThat(step.path("argsHmac").asString()).matches("[0-9a-f]{64}");
                assertThat(step.path("resultHmac").asString()).matches("[0-9a-f]{64}");
                assertThat(step.path("argsHmac").asString()).doesNotContain("HIRED");
            }
        }
    }

    private static java.util.List<String> deniedReasons(JsonNode reconstruction) {
        java.util.List<String> reasons = new java.util.ArrayList<>();
        for (JsonNode step : reconstruction.path("steps")) {
            if ("DENIED".equals(step.path("decision").asString())) {
                reasons.add(step.path("reasonCode").asString());
            }
        }
        return reasons;
    }
}
