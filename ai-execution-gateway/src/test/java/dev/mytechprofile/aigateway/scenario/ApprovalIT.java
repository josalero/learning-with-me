package dev.mytechprofile.aigateway.scenario;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.mytechprofile.aigateway.recruiting.transition.TransitionRequestRepository;
import dev.mytechprofile.aigateway.shared.security.ApiKeyAuthenticationFilter;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ApprovalIT extends GatewayIntegrationTest {

    @Autowired
    private TransitionRequestRepository transitions;

    @Test
    void servicePrincipalCannotApproveAndASecondApprovalConflicts() throws Exception {
        UUID executionId = startEvaluation(aliceKey, "C-101", "R-7");
        awaitTerminal(aliceKey, executionId);
        UUID requestId = transitions.findByExecutionId(executionId).getFirst().getId();

        int forbidden = mockMvc.perform(post("/api/v1/transition-requests/" + requestId + "/approve")
                        .header(ApiKeyAuthenticationFilter.HEADER, atsKey))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(forbidden).isEqualTo(403);

        int approved = mockMvc.perform(post("/api/v1/transition-requests/" + requestId + "/approve")
                        .header(ApiKeyAuthenticationFilter.HEADER, aliceKey))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(approved).isEqualTo(200);

        int conflict = mockMvc.perform(post("/api/v1/transition-requests/" + requestId + "/approve")
                        .header(ApiKeyAuthenticationFilter.HEADER, aliceKey))
                .andReturn()
                .getResponse()
                .getStatus();
        assertThat(conflict).isEqualTo(409);
    }
}
