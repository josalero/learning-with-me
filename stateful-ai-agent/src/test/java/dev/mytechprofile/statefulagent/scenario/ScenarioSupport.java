package dev.mytechprofile.statefulagent.scenario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.mytechprofile.statefulagent.agent.StubChatModel;
import dev.mytechprofile.statefulagent.approval.ToolApprovalRepository;
import dev.mytechprofile.statefulagent.business.HiringActionRepository;
import dev.mytechprofile.statefulagent.business.HiringStage;
import dev.mytechprofile.statefulagent.business.InterviewScorecardRepository;
import dev.mytechprofile.statefulagent.business.JobApplicationEntity;
import dev.mytechprofile.statefulagent.business.JobApplicationRepository;
import dev.mytechprofile.statefulagent.business.JobOfferRepository;
import dev.mytechprofile.statefulagent.tools.FailureGate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class ScenarioSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    int port;

    @Autowired
    JobApplicationRepository applications;

    @Autowired
    HiringActionRepository actions;

    @Autowired
    InterviewScorecardRepository scorecards;

    @Autowired
    JobOfferRepository offers;

    @Autowired
    ToolApprovalRepository approvals;

    @Autowired
    StubChatModel stub;

    @Autowired
    FailureGate failures;

    private RestClient http;

    @BeforeEach
    void resetHarness() {
        this.stub.reset();
        this.failures.reset();
        this.http = RestClient.builder().baseUrl("http://127.0.0.1:" + this.port).build();
    }

    protected String application(String tenant, HiringStage stage, long bandMin, long bandMax) {
        String id = "APP-" + UUID.randomUUID().toString().substring(0, 8);
        JobApplicationEntity entity = new JobApplicationEntity();
        entity.setId(id);
        entity.setTenantId(tenant);
        entity.setRoleTitle("Backend Engineer");
        entity.setStage(stage);
        entity.setBandMin(BigDecimal.valueOf(bandMin));
        entity.setBandMax(BigDecimal.valueOf(bandMax));
        this.applications.save(entity);
        return id;
    }

    protected String session(String user, String password) {
        return session(user, password, "STUB", "REJECT");
    }

    protected String session(String user, String password, String provider, String mode) {
        Map<String, Object> body = this.http.post()
                .uri("/api/v1/sessions")
                .headers(headers -> headers.setBasicAuth(user, password))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("provider", provider, "compatibilityMode", mode))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return String.valueOf(body.get("sessionId"));
    }

    protected ResponseEntity<Map<String, Object>> message(String user, String password, String sessionId, String text) {
        return exchange(user, password, HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/messages",
                Map.of("text", text), null);
    }

    protected ResponseEntity<Map<String, Object>> decide(String user, String password, String sessionId,
            String approvalId, String toolCallKey, String choice, String idempotencyKey) {
        Map<String, Object> call = choice.equals("DENY")
                ? Map.of("toolCallKey", toolCallKey, "decision", "DENY", "reason", "Not now")
                : Map.of("toolCallKey", toolCallKey, "decision", "ALLOW");
        return exchange(user, password, HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/approvals/" + approvalId + "/decision",
                Map.of("decisions", List.of(call)), idempotencyKey);
    }

    protected ResponseEntity<Map<String, Object>> exchange(String user, String password, HttpMethod method, String path,
            Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(user, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return this.http.method(method)
                .uri(path)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .body(body == null ? "" : body)
                .exchange((request, response) -> {
                    Map<String, Object> payload = response.getBody() == null || response.getBody().available() == 0
                            ? Map.of()
                            : response.bodyTo(new ParameterizedTypeReference<Map<String, Object>>() {
                            });
                    return ResponseEntity.status(response.getStatusCode()).body(payload);
                });
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> approval(Map<String, Object> body) {
        return (Map<String, Object>) body.get("approval");
    }

    @SuppressWarnings("unchecked")
    protected String toolCallKey(Map<String, Object> body) {
        Map<String, Object> approval = approval(body);
        List<Map<String, Object>> calls = (List<Map<String, Object>>) approval.get("calls");
        return String.valueOf(calls.get(0).get("toolCallKey"));
    }

    protected String approvalId(Map<String, Object> body) {
        return String.valueOf(approval(body).get("id"));
    }

    protected void allow(String user, String password, String sessionId, Map<String, Object> paused) {
        ResponseEntity<Map<String, Object>> decision = decide(user, password, sessionId, approvalId(paused),
                toolCallKey(paused), "ALLOW", UUID.randomUUID().toString());
        if (!decision.getStatusCode().is2xxSuccessful() || !"COMPLETED".equals(decision.getBody().get("status"))) {
            throw new IllegalStateException("Expected a completed approval, got " + decision.getStatusCode() + " "
                    + decision.getBody());
        }
    }
}
