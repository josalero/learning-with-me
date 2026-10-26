package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import dev.mytechprofile.statefulagent.business.HiringStage;

class ReadOnlyOperationScenarioTest extends ScenarioSupport {

    @Test
    void statusReadReturnsTheStageWithoutAnApproval() {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        ResponseEntity<Map<String, Object>> response = message("alice", "test-alice", sessionId, "status of " + id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(String.valueOf(response.getBody().get("answer"))).contains("RECRUITER_SCREEN");
        assertThat(approvals.findBySessionIdOrderByCreatedAtAsc(sessionId)).isEmpty();
    }
}
