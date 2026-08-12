package dev.mytechprofile.research.langchain4j.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.mytechprofile.research.langchain4j.agents.CriticAgent;
import dev.mytechprofile.research.langchain4j.agents.PlannerAgent;
import dev.mytechprofile.research.langchain4j.agents.ResearcherAgent;
import dev.mytechprofile.research.langchain4j.agents.WriterAgent;
import dev.mytechprofile.research.langchain4j.config.PromptResources;
import dev.mytechprofile.research.langchain4j.config.ResearchProperties;
import dev.mytechprofile.research.langchain4j.domain.Critique;
import dev.mytechprofile.research.langchain4j.domain.ResearchCommand;
import dev.mytechprofile.research.langchain4j.domain.ResearchReport;
import dev.mytechprofile.research.langchain4j.domain.StepEvent;

/**
 * Exercises the declarative sequence + loop with a scripted ChatModel (no network).
 */
class ResearchOrchestratorFakeModelTest {

    @Test
    void run_withPassingCritique_emitsTrackedStepsAndExitsLoopEarly() {
        Queue<String> responses = new ArrayDeque<>(List.of(
                """
                {"questions":["What problem do virtual threads solve?"]}
                """,
                """
                {"findings":[{"question":"What problem do virtual threads solve?","answer":"Cheaper blocking concurrency."}]}
                """,
                "# Draft report\n\nVirtual threads help.",
                """
                {"score":9,"notes":"Solid"}
                """
        ));
        ChatModel model = new ScriptedChatModel(responses);
        ResearchOrchestrator orchestrator = orchestrator(model, properties(7, 2));

        List<String> live = new ArrayList<>();
        ResearchReport report = orchestrator.run(
                new ResearchCommand("Java virtual threads", 1),
                event -> live.add(event.agent()));

        assertThat(responses).isEmpty();
        assertThat(live).contains("planner", "researcher", "writer", "critic");
        assertThat(report.steps()).extracting(StepEvent::agent)
                .contains("planner", "researcher", "writer", "critic");
        assertThat(report.critique().score()).isEqualTo(9);
        assertThat(report.finalReport()).contains("Virtual threads");
        assertThat(report.engine()).isEqualTo("langchain4j");
    }

    @Test
    void run_withFailingCritiques_respectsMaxRevisions() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("""
                {"questions":["Q1"]}
                """);
        responses.add("""
                {"findings":[{"question":"Q1","answer":"A1"}]}
                """);
        for (int i = 1; i <= 3; i++) {
            responses.add("# Draft " + i);
            responses.add("""
                    {"score":3,"notes":"Needs work"}
                    """);
        }

        ChatModel model = new ScriptedChatModel(responses);
        ResearchOrchestrator orchestrator = orchestrator(model, properties(7, 2));
        ResearchReport report = orchestrator.run(new ResearchCommand("topic", 1));

        assertThat(responses).isEmpty();
        long writerSteps = report.steps().stream().filter(s -> "writer".equals(s.agent())).count();
        long criticSteps = report.steps().stream().filter(s -> "critic".equals(s.agent())).count();
        assertThat(writerSteps).isEqualTo(3);
        assertThat(criticSteps).isEqualTo(3);
        assertThat(report.critique().passes(7)).isFalse();
    }

    private static ResearchOrchestrator orchestrator(ChatModel model, ResearchProperties properties) {
        PlannerAgent planner = AgenticServices.agentBuilder(PlannerAgent.class)
                .chatModel(model)
                .name("planner")
                .systemMessage(PromptResources.load("planner.system.txt"))
                .outputKey("plan")
                .build();
        ResearcherAgent researcher = AgenticServices.agentBuilder(ResearcherAgent.class)
                .chatModel(model)
                .name("researcher")
                .systemMessage(PromptResources.load("researcher.system.txt"))
                .outputKey("findingsDoc")
                .build();
        WriterAgent writer = AgenticServices.agentBuilder(WriterAgent.class)
                .chatModel(model)
                .name("writer")
                .systemMessage(PromptResources.load("writer.system.txt"))
                .outputKey("draft")
                .build();
        CriticAgent critic = AgenticServices.agentBuilder(CriticAgent.class)
                .chatModel(model)
                .name("critic")
                .systemMessage(PromptResources.load("critic.system.txt"))
                .outputKey("critique")
                .build();

        int maxIterations = properties.maxRevisions() + 1;
        int passThreshold = properties.passThreshold();
        UntypedAgent reviewLoop = AgenticServices.loopBuilder()
                .subAgents(writer, critic)
                .maxIterations(maxIterations)
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> {
                    Object critiqueState = scope.readState("critique");
                    if (critiqueState instanceof Critique c) {
                        return c.passes(passThreshold);
                    }
                    return false;
                })
                .build();

        return new ResearchOrchestrator(
                planner,
                researcher,
                reviewLoop,
                new ReportAssembler(properties),
                properties);
    }

    private static ResearchProperties properties(int threshold, int maxRevisions) {
        return new ResearchProperties(
                "langchain4j",
                "LangChain4j",
                "1.18.1",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini:online",
                threshold,
                maxRevisions,
                3,
                600_000L,
                0.2,
                4096,
                new ResearchProperties.OpenRouter("test-key", "https://openrouter.ai/api/v1")
        );
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> responses;

        private ScriptedChatModel(Queue<String> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            String next = responses.poll();
            if (next == null) {
                throw new IllegalStateException("ScriptedChatModel has no more responses");
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(next.trim()))
                    .build();
        }
    }
}
