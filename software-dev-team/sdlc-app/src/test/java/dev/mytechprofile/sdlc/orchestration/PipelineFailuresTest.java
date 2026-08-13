package dev.mytechprofile.sdlc.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class PipelineFailuresTest {

    @Test
    void userMessage_unwrapsUntypedAgentToTheNamedRoleAndRootCause() {
        Throwable nested = new AgentInvocationException(
                "Failed to invoke agent method: public abstract"
                        + " dev.mytechprofile.sdlc.domain.ReviewVerdict"
                        + " dev.mytechprofile.sdlc.agent.PrReviewerAgent.review("
                        + "dev.mytechprofile.sdlc.domain.AiSpec,java.lang.String,java.lang.String)",
                new IllegalArgumentException("Failed to parse null"));
        Throwable outer = new AgentInvocationException(
                "Failed to invoke agent method: public abstract java.lang.Object"
                        + " dev.langchain4j.agentic.UntypedAgent.invoke(java.util.Map)",
                new AgentInvocationException(new InvocationTargetException(nested)));

        String message = PipelineFailures.userMessage(outer);

        assertThat(message)
                .contains("Pipeline failed at pr-reviewer:")
                .contains("Failed to parse null")
                .doesNotContain("UntypedAgent");
    }

    @Test
    void userMessage_whenThereIsNoNamedAgent_keepsTheRootDetail() {
        String message = PipelineFailures.userMessage(new IllegalStateException("OPENROUTER_API_KEY is not set"));

        assertThat(message).contains("Pipeline failed: OPENROUTER_API_KEY is not set");
    }
}
