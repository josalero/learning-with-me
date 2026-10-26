package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import dev.mytechprofile.statefulagent.business.HiringStage;

class ProviderSwitchScenarioTest extends ScenarioSupport {

    @Test
    void rejectModeStopsOnForeignPartsAndStripModeLetsTheStubContinue() {
        String rejected = session("alice", "test-alice", "STUB_FOREIGN", "REJECT");
        assertThat(message("alice", "test-alice", rejected, "hello").getStatusCode()).isEqualTo(HttpStatus.OK);
        exchange("alice", "test-alice", HttpMethod.PATCH, "/api/v1/sessions/" + rejected,
                java.util.Map.of("provider", "STUB", "compatibilityMode", "REJECT"), null);
        var blocked = message("alice", "test-alice", rejected, "note: after switch");
        assertThat(blocked.getStatusCode().value()).isEqualTo(422);

        String stripped = session("alice", "test-alice", "STUB_FOREIGN", "STRIP_PROVIDER_PAYLOAD");
        assertThat(message("alice", "test-alice", stripped, "hello").getStatusCode()).isEqualTo(HttpStatus.OK);
        exchange("alice", "test-alice", HttpMethod.PATCH, "/api/v1/sessions/" + stripped,
                java.util.Map.of("provider", "STUB"), null);
        var continued = message("alice", "test-alice", stripped, "note: kept");
        assertThat(continued.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stub.prompts()).isNotEmpty();
    }
}
