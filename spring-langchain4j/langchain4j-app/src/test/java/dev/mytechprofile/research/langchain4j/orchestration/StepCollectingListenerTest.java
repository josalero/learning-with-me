package dev.mytechprofile.research.langchain4j.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.research.langchain4j.domain.StepEvent;

class StepCollectingListenerTest {

    @Test
    void afterAgentInvocation_recordsTrackedAgentsAndNotifiesListeners() {
        StepCollectingListener listener = new StepCollectingListener();
        List<String> seen = new ArrayList<>();
        listener.addListener(event -> seen.add(event.agent()));

        listener.beforeAgentInvocation(request("planner-1", "PlannerAgent", Map.of("topic", "Java", "depth", 2)));
        listener.afterAgentInvocation(response(
                "planner-1",
                "PlannerAgent",
                Map.of("topic", "Java", "depth", 2),
                "planned questions"));

        listener.beforeAgentInvocation(request("sequence-1", "sequence", Map.of()));
        listener.afterAgentInvocation(response("sequence-1", "sequence", Map.of(), "ignored"));

        listener.beforeAgentInvocation(request("writer-1", "WriterAgent", Map.of("topic", "Java")));
        listener.afterAgentInvocation(response(
                "writer-1",
                "WriterAgent",
                Map.of("topic", "Java"),
                "drafted report"));

        assertThat(seen).containsExactly("planner", "writer");
        assertThat(listener.steps()).extracting(StepEvent::output)
                .containsExactly("planned questions", "drafted report");
        assertThat(listener.steps().getFirst().input()).contains("topic:").contains("Java");
        assertThat(listener.steps().getFirst().input()).contains("depth:");
    }

    @Test
    void nestedAgents_doNotClobberElapsedTiming() {
        StepCollectingListener listener = new StepCollectingListener();

        listener.beforeAgentInvocation(request("parent", "sequence", Map.of()));
        listener.beforeAgentInvocation(request("child", "planner", Map.of("topic", "t")));
        listener.afterAgentInvocation(response("child", "planner", Map.of("topic", "t"), "planned"));
        listener.afterAgentInvocation(response("parent", "sequence", Map.of(), "done"));

        assertThat(listener.steps()).hasSize(1);
        assertThat(listener.steps().getFirst().agent()).isEqualTo("planner");
        assertThat(listener.steps().getFirst().elapsedMs()).isGreaterThanOrEqualTo(0L);
        assertThat(listener.steps().getFirst().input()).contains("topic:");
        assertThat(listener.steps().getFirst().output()).isEqualTo("planned");
    }

    private static AgentRequest request(String agentId, String name, Map<String, Object> inputs) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.name()).thenReturn(name);
        when(agent.agentId()).thenReturn(agentId);
        return new AgentRequest(mock(AgenticScope.class), agent, inputs);
    }

    private static AgentResponse response(
            String agentId,
            String name,
            Map<String, Object> inputs,
            String output) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.name()).thenReturn(name);
        when(agent.agentId()).thenReturn(agentId);
        return new AgentResponse(mock(AgenticScope.class), agent, inputs, output, null, null);
    }
}
