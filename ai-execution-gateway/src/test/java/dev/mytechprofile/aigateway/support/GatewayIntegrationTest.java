package dev.mytechprofile.aigateway.support;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.mytechprofile.aigateway.security.ApiKeyAuthenticationFilter;
import dev.mytechprofile.aigateway.security.ApiKeyHasher;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTelemetryConfig.class)
public abstract class GatewayIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gateway.audit.hmac-key", () -> "test-hmac-key");
        registry.add("management.tracing.export.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    protected String aliceKey;
    protected String atsKey;
    protected String bobKey;

    @BeforeEach
    void registerKeys() {
        jdbc.update("delete from transition_request");
        jdbc.update("delete from recommendation_draft");
        jdbc.update("delete from execution_step");
        jdbc.update("delete from execution");
        jdbc.update("update candidate set status = 'SCREENING'");
        aliceKey = register("recruiter-alice");
        atsKey = register("ats-nightly");
        bobKey = register("recruiter-bob");
    }

    protected UUID startEvaluation(String apiKey, String candidateId, String requisitionId) throws Exception {
        String body = """
                {"agentId":"screening-agent","taskType":"CANDIDATE_EVALUATION","candidateId":"%s","requisitionId":"%s"}
                """
                .formatted(candidateId, requisitionId);
        var response = mockMvc.perform(post("/api/v1/executions")
                        .header(ApiKeyAuthenticationFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
        if (response.getStatus() != 202) {
            throw new IllegalStateException("expected 202 but got " + response.getStatus() + " " + response.getContentAsString());
        }
        return UUID.fromString(json.readTree(response.getContentAsString()).path("executionId").asString());
    }

    protected JsonNode awaitTerminal(String apiKey, UUID executionId) {
        AtomicReference<JsonNode> body = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            String response = mockMvc.perform(get("/api/v1/executions/" + executionId)
                            .header(ApiKeyAuthenticationFilter.HEADER, apiKey))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode node = json.readTree(response);
            body.set(node);
            String status = node.path("status").asString();
            return "COMPLETED".equals(status) || "BUDGET_EXHAUSTED".equals(status) || "FAILED".equals(status);
        });
        return body.get();
    }

    protected JsonNode reconstruction(String apiKey, UUID executionId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/executions/" + executionId + "/reconstruction")
                        .header(ApiKeyAuthenticationFilter.HEADER, apiKey))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    private String register(String principalId) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String raw = "gw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("delete from api_key where principal_id = ?", principalId);
        jdbc.update(
                "insert into api_key (id, principal_id, key_hash, created_at) values (?, ?, ?, now())",
                UUID.randomUUID(),
                principalId,
                ApiKeyHasher.sha256(raw));
        return raw;
    }
}
