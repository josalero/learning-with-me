package dev.mytechprofile.statefulagent.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.mytechprofile.statefulagent.StatefulAgentApplication;
import dev.mytechprofile.statefulagent.business.HiringStage;

@Testcontainers
class RestartWhilePendingScenarioTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Test
    void aPendingScheduleSurvivesAProcessRestart() {
        postgres.start();
        try (ConfigurableApplicationContext first = start(0)) {
            RestClient http = client(first);
            String sessionId = create(http);
            Map<String, Object> paused = http.post()
                    .uri("/api/v1/sessions/{id}/messages", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", "schedule tech screen for APP-1"))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            assertThat(paused.get("status")).isEqualTo("AWAITING_APPROVAL");
            first.close();
            try (ConfigurableApplicationContext second = start(0)) {
                RestClient resumed = client(second);
                @SuppressWarnings("unchecked")
                Map<String, Object> approval = (Map<String, Object>) paused.get("approval");
                @SuppressWarnings("unchecked")
                var calls = (java.util.List<Map<String, Object>>) approval.get("calls");
                Map<String, Object> done = resumed.post()
                        .uri("/api/v1/sessions/{session}/approvals/{approval}/decision", sessionId, approval.get("id"))
                        .header("Idempotency-Key", "after-restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("decisions", java.util.List.of(Map.of(
                                "toolCallKey", calls.get(0).get("toolCallKey"),
                                "decision", "ALLOW"))))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                assertThat(done.get("status")).isEqualTo("COMPLETED");
                Map<String, Object> application = resumed.get()
                        .uri("/api/v1/applications/APP-1")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                assertThat(application.get("stage")).isEqualTo(HiringStage.TECH_SCREEN.name());
            }
        }
    }

    private static ConfigurableApplicationContext start(int port) {
        return new SpringApplicationBuilder(StatefulAgentApplication.class)
                .profiles("test")
                .properties(
                        "server.port=" + port,
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword())
                .run();
    }

    private static RestClient client(ConfigurableApplicationContext context) {
        int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
                .defaultHeaders(headers -> headers.setBasicAuth("alice", "test-alice"))
                .build();
    }

    private static String create(RestClient http) {
        Map<String, Object> created = http.post()
                .uri("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("provider", "STUB"))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return String.valueOf(created.get("sessionId"));
    }
}
