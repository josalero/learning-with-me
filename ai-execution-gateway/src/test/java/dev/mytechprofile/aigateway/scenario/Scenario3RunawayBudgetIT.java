package dev.mytechprofile.aigateway.scenario;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.mytechprofile.aigateway.recruiting.candidate.CandidateRepository;
import dev.mytechprofile.aigateway.recruiting.candidate.CandidateStatus;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.JsonNode;

class Scenario3RunawayBudgetIT extends GatewayIntegrationTest {

    @Autowired
    private CandidateRepository candidates;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void sixthToolCallIsNeverExecuted() throws Exception {
        UUID executionId = startEvaluation(atsKey, "C-103", "R-7");
        JsonNode done = awaitTerminal(atsKey, executionId);

        assertThat(done.path("status").asString()).isEqualTo("BUDGET_EXHAUSTED");
        assertThat(done.path("delegatorId").asString()).isEqualTo("ats-nightly");
        assertThat(done.path("evaluation").path("verdict").asString()).isEqualTo("NOT_EVALUATED");
        assertThat(done.path("evaluation").path("successful").asBoolean()).isFalse();
        assertThat(candidates.findById("C-103").orElseThrow().getStatus()).isEqualTo(CandidateStatus.SCREENING);

        JsonNode reconstruction = reconstruction(atsKey, executionId);
        assertThat(reconstruction.path("delegator").path("kind").asString()).isEqualTo("SERVICE");
        assertThat(reconstruction.path("totals").path("modelCalls").asInt()).isEqualTo(6);
        assertThat(reconstruction.path("totals").path("toolCallsRequested").asInt()).isEqualTo(5);
        assertThat(reconstruction.path("totals").path("toolCallsPermitted").asInt()).isEqualTo(5);
        assertThat(reconstruction.path("totals").path("inputTokens").asInt()).isEqualTo(3600);
        assertThat(reconstruction.path("totals").path("outputTokens").asInt()).isEqualTo(900);
        assertThat(reconstruction.path("totals").path("costMicros").asLong()).isEqualTo(3150);
        assertThat(reconstruction.path("steps")).noneMatch(step ->
                "TOOL".equals(step.path("type").asString()) && step.path("seq").asInt() > 11);

        assertThat(redis.opsForValue().get("budget:" + executionId + ":toolCalls")).isEqualTo("5");
        assertThat(Long.parseLong(redis.opsForValue().get("budget:" + executionId + ":tokens"))).isLessThanOrEqualTo(8000);
        assertThat(Long.parseLong(redis.opsForValue().get("budget:" + executionId + ":modelCalls"))).isLessThanOrEqualTo(20);
        assertThat(Long.parseLong(redis.opsForValue().get("budget:" + executionId + ":costMicros"))).isLessThanOrEqualTo(50_000);
    }
}
