package dev.mytechprofile.research.springai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.research.springai.agents.CriticAgent;
import dev.mytechprofile.research.springai.agents.PlannerAgent;
import dev.mytechprofile.research.springai.agents.ResearcherAgent;
import dev.mytechprofile.research.springai.agents.WriterAgent;
import dev.mytechprofile.research.springai.config.ResearchProperties;
import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.Finding;
import dev.mytechprofile.research.springai.domain.ResearchCommand;
import dev.mytechprofile.research.springai.domain.ResearchPlan;
import dev.mytechprofile.research.springai.domain.ResearchReport;

class ResearchOrchestratorTest {

    @Test
    void run_recordsPlannerResearcherWriterCriticInOrder_andExitsEarlyWhenScorePasses() {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger critiques = new AtomicInteger();

        PlannerAgent planner = (topic, depth) ->
                new ResearchPlan(List.of("Q1", "Q2", "Q3").subList(0, depth));
        ResearcherAgent researcher = plan -> plan.questions().stream()
                .map(q -> new Finding(q, "answer for " + q))
                .toList();
        WriterAgent writer = (topic, findings, previousCritique) -> {
            writes.incrementAndGet();
            return "# Draft " + writes.get();
        };
        CriticAgent critic = draft -> {
            critiques.incrementAndGet();
            return new Critique(9, "Looks good");
        };

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                planner, researcher, writer, critic, properties(7, 2));
        List<String> seen = new ArrayList<>();
        ResearchReport report = orchestrator.run(
                new ResearchCommand("Java virtual threads", 3),
                event -> seen.add(event.agent()));

        assertThat(seen).containsExactly("planner", "researcher", "writer", "critic");
        assertThat(writes.get()).isEqualTo(1);
        assertThat(critiques.get()).isEqualTo(1);
        assertThat(report.critique().score()).isEqualTo(9);
        assertThat(report.finalReport()).contains("Draft 1");
        assertThat(report.engine()).isEqualTo("spring-ai");
        assertThat(report.steps().getFirst().input()).contains("topic: Java virtual threads");
        assertThat(report.steps().getFirst().output()).contains("Q1");
        assertThat(report.steps().get(2).output()).contains("Draft 1");
        assertThat(report.steps().get(3).output()).contains("score: 9");
    }

    @Test
    void run_stopsAtMaxRevisions_whenCriticNeverPasses() {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger critiques = new AtomicInteger();

        PlannerAgent planner = (topic, depth) -> new ResearchPlan(List.of("Q1"));
        ResearcherAgent researcher = plan -> List.of(new Finding("Q1", "A1"));
        WriterAgent writer = (topic, findings, previousCritique) -> "# Draft " + writes.incrementAndGet();
        CriticAgent critic = draft -> {
            critiques.incrementAndGet();
            return new Critique(3, "Needs work");
        };

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(
                planner, researcher, writer, critic, properties(7, 2));
        ResearchReport report = orchestrator.run(new ResearchCommand("topic", 1));

        assertThat(writes.get()).isEqualTo(3);
        assertThat(critiques.get()).isEqualTo(3);
        assertThat(report.steps()).extracting(s -> s.agent())
                .containsExactly(
                        "planner", "researcher",
                        "writer", "critic",
                        "writer", "critic",
                        "writer", "critic");
        assertThat(report.critique().passes(7)).isFalse();
    }

    @Test
    void run_notifiesStepListeners() {
        List<String> seen = new ArrayList<>();
        PlannerAgent planner = (topic, depth) -> new ResearchPlan(List.of("Q1"));
        ResearcherAgent researcher = plan -> List.of(new Finding("Q1", "A1"));
        WriterAgent writer = (topic, findings, previousCritique) -> "draft";
        CriticAgent critic = draft -> new Critique(8, "ok");

        new ResearchOrchestrator(planner, researcher, writer, critic, properties(7, 1))
                .run(new ResearchCommand("t", 1), event -> seen.add(event.agent()));

        assertThat(seen).containsExactly("planner", "researcher", "writer", "critic");
    }

    private static ResearchProperties properties(int threshold, int maxRevisions) {
        return new ResearchProperties(
                "spring-ai",
                "Spring AI",
                "2.0.0",
                "openai/gpt-4o-mini",
                "openai/gpt-4o-mini:online",
                threshold,
                maxRevisions,
                3,
                600_000L
        );
    }
}
