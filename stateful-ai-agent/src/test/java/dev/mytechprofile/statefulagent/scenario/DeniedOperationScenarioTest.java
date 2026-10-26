package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import dev.mytechprofile.statefulagent.business.HiringStage;

class DeniedOperationScenarioTest extends ScenarioSupport {

    @Test
    void denyingThePauseLeavesTheStageUnchanged() {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        ResponseEntity<Map<String, Object>> paused = message("alice", "test-alice", sessionId,
                "schedule tech screen for " + id);
        ResponseEntity<Map<String, Object>> denied = decide("alice", "test-alice", sessionId, approvalId(paused.getBody()),
                toolCallKey(paused.getBody()), "DENY", "deny-1");
        assertThat(denied.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(denied.getBody().get("status")).isEqualTo("DENIED");
        assertThat(String.valueOf(denied.getBody().get("answer"))).contains("Not now");
        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.RECRUITER_SCREEN);
        assertThat(actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).isEmpty();
    }
}
