package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import dev.mytechprofile.statefulagent.business.HiringStage;

class DoubleApprovalScenarioTest extends ScenarioSupport {

    @Test
    void twoDecisionsWithTheSameKeyRecordOneAdvance() throws Exception {
        String id = application("tenant-a", HiringStage.RECRUITER_SCREEN, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        Map<String, Object> paused = message("alice", "test-alice", sessionId, "schedule tech screen for " + id).getBody();
        String approval = approvalId(paused);
        String key = toolCallKey(paused);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> decide("alice", "test-alice", sessionId, approval, key, "ALLOW", "same-key"));
            Future<?> second = pool.submit(() -> decide("alice", "test-alice", sessionId, approval, key, "ALLOW", "same-key"));
            first.get();
            second.get();
        }
        finally {
            pool.shutdown();
        }
        assertThat(actions.findByTenantIdAndApplicationIdOrderByCreatedAtDesc("tenant-a", id)).hasSize(1);
        assertThat(applications.findById(id).orElseThrow().getStage()).isEqualTo(HiringStage.TECH_SCREEN);

        var conflict = decide("alice", "test-alice", sessionId, approval, key, "ALLOW", "other-key");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
