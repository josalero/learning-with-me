package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class CompactionScenarioTest extends ScenarioSupport {

    @Test
    void theFourthShortNoteCompactsWhileTheFullLogRemains() {
        String sessionId = session("alice", "test-alice");
        for (int turn = 1; turn <= 4; turn++) {
            var response = message("alice", "test-alice", sessionId, "note: turn " + turn);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        List<Map<String, Object>> all = events(sessionId, "all");
        List<Map<String, Object>> active = events(sessionId, "active");
        assertThat(all.stream().map(event -> String.valueOf(event.get("type")))).anyMatch(type -> type.contains("COMPACT"));
        assertThat(active).isNotEmpty();
        assertThat(all.size()).isGreaterThan(active.size());
    }

    private List<Map<String, Object>> events(String sessionId, String view) {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + this.port).build().get()
                .uri("/api/v1/sessions/{id}/events?view={view}", sessionId, view)
                .headers(headers -> headers.setBasicAuth("alice", "test-alice"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
