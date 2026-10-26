package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.statefulagent.business.HiringStage;

class TraceCorrelationScenarioTest extends ScenarioSupport {

    @Test
    void thePausedApprovalStoresTheTurnTrace() {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        var paused = message("alice", "test-alice", sessionId, "schedule tech screen for " + id);
        var stored = approvals.findById(java.util.UUID.fromString(approvalId(paused.getBody()))).orElseThrow();
        assertThat(stored.getTraceId()).isNotBlank();
        assertThat(stored.getSpanId()).isNotBlank();
        allow("alice", "test-alice", sessionId, paused.getBody());
        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.TECH_SCREEN);
    }
}
