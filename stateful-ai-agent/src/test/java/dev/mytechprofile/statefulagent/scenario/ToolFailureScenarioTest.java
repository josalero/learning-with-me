package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewRound;

class ToolFailureScenarioTest extends ScenarioSupport {

    @Test
    void aTransientScorecardFailureRetriesOnceAndWritesOneRow() {
        String id = application("tenant-a", HiringStage.TECH_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        failures.failNext(1);
        var paused = message("alice", "test-alice", sessionId, "tech screen for " + id + " was yes");
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var failed = decide("alice", "test-alice", sessionId, approvalId(paused.getBody()),
                toolCallKey(paused.getBody()), "ALLOW", "scorecard-1");
        assertThat(failed.getBody().get("status")).isEqualTo("FAILED");
        assertThat(scorecards.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).isEmpty();

        var retried = exchange("alice", "test-alice", HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/approvals/" + approvalId(paused.getBody()) + "/retry",
                java.util.Map.of(), "scorecard-1");
        assertThat(retried.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(scorecards.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).hasSize(1);
        assertThat(scorecards.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id).get(0).getRound())
                .isEqualTo(InterviewRound.TECH_SCREEN);
    }
}
