package dev.mytechprofile.aigateway.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import dev.mytechprofile.aigateway.security.ApiKeyAuthenticationFilter;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TenantIsolationIT extends GatewayIntegrationTest {

    @Test
    void foreignCandidateAndExecutionLookMissingAndBadKeysAreUnauthorized() throws Exception {
        int foreignCandidate = mockMvc.perform(post("/api/v1/executions")
                        .header(ApiKeyAuthenticationFilter.HEADER, bobKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"screening-agent","taskType":"CANDIDATE_EVALUATION","candidateId":"C-101","requisitionId":"R-7"}
                                """))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(foreignCandidate).isEqualTo(404);

        UUID aliceExecution = startEvaluation(aliceKey, "C-101", "R-7");
        int foreignExecution = mockMvc.perform(get("/api/v1/executions/" + aliceExecution)
                        .header(ApiKeyAuthenticationFilter.HEADER, bobKey))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(foreignExecution).isEqualTo(404);

        int missing = mockMvc.perform(get("/api/v1/executions/" + aliceExecution))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(missing).isEqualTo(401);

        int malformed = mockMvc.perform(get("/api/v1/executions/" + aliceExecution)
                        .header(ApiKeyAuthenticationFilter.HEADER, "gw_not-a-key"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(malformed).isEqualTo(401);
        awaitTerminal(aliceKey, aliceExecution);
    }
}
