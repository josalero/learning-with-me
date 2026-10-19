package dev.mytechprofile.aigateway.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.mytechprofile.aigateway.domain.CandidateStatus;
import dev.mytechprofile.aigateway.persistence.CandidateRepository;
import dev.mytechprofile.aigateway.security.ApiKeyAuthenticationFilter;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import tools.jackson.databind.JsonNode;

@ExtendWith(OutputCaptureExtension.class)
class Scenario1HappyPathIT extends GatewayIntegrationTest {

    private static final String NAME = "CANARY-NAME-7731";
    private static final String EMAIL = "canary7731@example.test";

    @Autowired
    private CandidateRepository candidates;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemorySpanExporter spans;

    @Test
    void recruiterEvaluationCompletesAndStatusChangesOnlyAfterApproval(CapturedOutput output) throws Exception {
        spans.reset();
        assertThat(candidates.findById("C-101").orElseThrow().getStatus()).isEqualTo(CandidateStatus.SCREENING);

        UUID executionId = startEvaluation(aliceKey, "C-101", "R-7");
        JsonNode done = awaitTerminal(aliceKey, executionId);
        assertThat(done.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.path("evaluation").path("verdict").asString()).isEqualTo("PASSED");
        assertThat(done.path("evaluation").path("successful").asBoolean()).isTrue();
        assertThat(candidates.findById("C-101").orElseThrow().getStatus()).isEqualTo(CandidateStatus.SCREENING);

        JsonNode reconstruction = reconstruction(aliceKey, executionId);
        assertThat(reconstruction.path("agentId").asString()).isEqualTo("screening-agent");
        assertThat(reconstruction.path("delegator").path("id").asString()).isEqualTo("recruiter-alice");
        assertThat(reconstruction.path("delegator").path("kind").asString()).isEqualTo("USER");
        assertThat(reconstruction.path("traceId").asString()).isNotBlank();
        assertThat(reconstruction.path("totals").path("modelCalls").asInt()).isEqualTo(5);
        assertThat(reconstruction.path("totals").path("toolCallsRequested").asInt()).isEqualTo(4);
        assertThat(reconstruction.path("totals").path("toolCallsPermitted").asInt()).isEqualTo(4);
        assertThat(reconstruction.path("totals").path("toolCallsDenied").asInt()).isZero();
        assertThat(reconstruction.path("totals").path("inputTokens").asInt()).isEqualTo(3000);
        assertThat(reconstruction.path("totals").path("outputTokens").asInt()).isEqualTo(750);
        assertThat(reconstruction.path("totals").path("costMicros").asLong()).isEqualTo(2625);
        assertThat(reconstruction.path("evaluation").path("verdict").asString()).isEqualTo("PASSED");

        assertThat(reconstruction.path("steps")).hasSize(9);
        for (JsonNode step : reconstruction.path("steps")) {
            assertThat(step.path("spanId").asString()).isNotBlank();
            if ("TOOL".equals(step.path("type").asString())) {
                assertThat(step.path("decision").asString()).isEqualTo("PERMITTED");
                assertThat(step.path("argsHmac").asString()).hasSize(64);
                assertThat(step.path("resultHmac").asString()).hasSize(64);
            }
        }
        assertThat(toolNames(reconstruction))
                .containsExactly(
                        "get_candidate_summary", "search_evidence", "draft_recommendation", "request_status_transition");

        String listed = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/executions/" + executionId + "/transition-requests")
                        .header(ApiKeyAuthenticationFilter.HEADER, aliceKey))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pending = json.readTree(listed);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).path("status").asString()).isEqualTo("PENDING");
        assertThat(pending.get(0).path("from").asString()).isEqualTo("SCREENING");
        assertThat(pending.get(0).path("to").asString()).isEqualTo("INTERVIEW");
        String approved = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/transition-requests/" + pending.get(0).path("transitionRequestId").asString()
                                        + "/approve")
                        .header(ApiKeyAuthenticationFilter.HEADER, aliceKey))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(approved).path("candidateStatus").asString()).isEqualTo("INTERVIEW");
        assertThat(candidates.findById("C-101").orElseThrow().getStatus()).isEqualTo(CandidateStatus.INTERVIEW);

        assertNoCanary(output.getAll());
        for (SpanData span : spans.getFinishedSpanItems()) {
            assertNoCanary(span.toString());
            if (executionId.toString().equals(span.getAttributes().asMap().toString())
                    || span.getAttributes().asMap().values().stream().anyMatch(value -> executionId.toString().equals(String.valueOf(value)))) {
                assertThat(span.getTraceId()).isEqualTo(reconstruction.path("traceId").asString());
            }
        }
        jdbc.query("select * from execution_step where execution_id = ?", rs -> {
            for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                assertNoCanary(String.valueOf(rs.getObject(column)));
            }
        }, executionId);
    }

    private static java.util.List<String> toolNames(JsonNode reconstruction) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode step : reconstruction.path("steps")) {
            if ("TOOL".equals(step.path("type").asString())) {
                names.add(step.path("tool").asString());
            }
        }
        return names;
    }

    private static void assertNoCanary(String value) {
        assertThat(value).doesNotContain(NAME).doesNotContain(EMAIL);
    }
}
