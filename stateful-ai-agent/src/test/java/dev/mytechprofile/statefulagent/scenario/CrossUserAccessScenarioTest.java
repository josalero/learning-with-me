package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import dev.mytechprofile.statefulagent.business.HiringStage;

class CrossUserAccessScenarioTest extends ScenarioSupport {

    @Test
    void foreignReadersGet404AndOnlyAnApproverCanAllowAnAboveBandOffer() {
        String id = application("tenant-a", HiringStage.OFFER, 120000, 160000);
        String sessionId = session("alice", "test-alice");
        var paused = message("alice", "test-alice", sessionId, "extend offer for " + id + " L4 190000");
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(approval(paused.getBody()).get("risk")).isEqualTo("HIGH");

        assertThat(exchange("bob", "test-bob", HttpMethod.GET, "/api/v1/sessions/" + sessionId, null, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("carol", "test-carol", HttpMethod.GET, "/api/v1/sessions/" + sessionId, null, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("dave", "test-dave", HttpMethod.GET, "/api/v1/sessions/" + sessionId, null, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("alice", "test-alice", HttpMethod.GET, "/api/v1/sessions/not-a-uuid", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var forbidden = decide("alice", "test-alice", sessionId, approvalId(paused.getBody()),
                toolCallKey(paused.getBody()), "ALLOW", "alice-high");
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var allowed = decide("bob", "test-bob", sessionId, approvalId(paused.getBody()),
                toolCallKey(paused.getBody()), "ALLOW", "bob-high");
        assertThat(allowed.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(offers.findByTenantIdAndApplicationId("tenant-a", id)).isPresent();
    }
}
