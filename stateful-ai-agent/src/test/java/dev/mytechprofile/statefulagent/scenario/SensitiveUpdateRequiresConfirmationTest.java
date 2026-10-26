package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import dev.mytechprofile.statefulagent.business.HiringStage;

class SensitiveUpdateRequiresConfirmationTest extends ScenarioSupport {

    @Test
    void schedulingPausesAndARejectByARecruiterIsDeniedImmediately() {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        ResponseEntity<Map<String, Object>> paused = message("alice", "test-alice", sessionId,
                "schedule tech screen for " + id);
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(paused.getBody().get("status")).isEqualTo("AWAITING_APPROVAL");
        assertThat(approval(paused.getBody()).get("risk")).isEqualTo("MEDIUM");
        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.RECRUITER_SCREEN);
        assertThat(actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).isEmpty();

        ResponseEntity<Map<String, Object>> deniedSchedule = decide("alice", "test-alice", sessionId,
                approvalId(paused.getBody()), toolCallKey(paused.getBody()), "DENY", "deny-the-schedule");
        assertThat(deniedSchedule.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.RECRUITER_SCREEN);

        ResponseEntity<Map<String, Object>> denied = message("alice", "test-alice", sessionId,
                "reject " + id + " bar not met");
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(denied.getBody().get("status")).isEqualTo("DENIED");
        assertThat(actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).isEmpty();
    }
}
