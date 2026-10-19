package dev.mytechprofile.aigateway.scenario;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import dev.mytechprofile.aigateway.gateway.model.ModelGateway;
import dev.mytechprofile.aigateway.shared.security.ApiKeyAuthenticationFilter;
import dev.mytechprofile.aigateway.support.GatewayIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import tools.jackson.databind.JsonNode;

class ExecutionLifecycleIT extends GatewayIntegrationTest {

    @MockitoSpyBean
    private ModelGateway models;

    @Test
    void pollerSeesRunningWhileTheModelIsWorking() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await(10, TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(models)
                .complete(any(), any());

        UUID executionId = startEvaluation(aliceKey, "C-101", "R-7");
        await().atMost(Duration.ofSeconds(5)).until(() -> "RUNNING".equals(status(executionId)));
        release.countDown();

        assertThat(awaitTerminal(aliceKey, executionId).path("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void modelFailureEndsAsFailedAndNotEvaluated() throws Exception {
        doThrow(new IllegalStateException("model unavailable")).when(models).complete(any(), any());

        UUID executionId = startEvaluation(aliceKey, "C-101", "R-7");
        JsonNode done = awaitTerminal(aliceKey, executionId);

        assertThat(done.path("status").asString()).isEqualTo("FAILED");
        assertThat(done.path("evaluation").path("verdict").asString()).isEqualTo("NOT_EVALUATED");
        assertThat(done.path("evaluation").path("successful").asBoolean()).isFalse();
    }

    private String status(UUID executionId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/executions/" + executionId)
                        .header(ApiKeyAuthenticationFilter.HEADER, aliceKey))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).path("status").asString();
    }
}
