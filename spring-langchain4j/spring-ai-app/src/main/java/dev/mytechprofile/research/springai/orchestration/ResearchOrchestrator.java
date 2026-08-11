package dev.mytechprofile.research.springai.orchestration;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import dev.mytechprofile.research.springai.domain.ResearchRole;
import dev.mytechprofile.research.springai.domain.StepEvent;

/**
 * Explicit Spring AI orchestration: planner → researcher → writer/critic loop.
 *
 * <p>Scenario: topic "Java virtual threads", depth 3 → plan questions, research each
 * with the online model, draft a report, critique until score ≥ threshold or max revisions.
 */
@Service
public class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);

    private final PlannerAgent planner;
    private final ResearcherAgent researcher;
    private final WriterAgent writer;
    private final CriticAgent critic;
    private final ResearchProperties properties;

    public ResearchOrchestrator(
            PlannerAgent planner,
            ResearcherAgent researcher,
            WriterAgent writer,
            CriticAgent critic,
            ResearchProperties properties) {
        this.planner = planner;
        this.researcher = researcher;
        this.writer = writer;
        this.critic = critic;
        this.properties = properties;
    }

    public ResearchReport run(ResearchCommand command) {
        return run(command, event -> {
        });
    }

    public ResearchReport run(ResearchCommand command, Consumer<StepEvent> stepListener) {
        StepTrace trace = new StepTrace();
        trace.addListener(stepListener);

        long started = System.currentTimeMillis();
        String topic = command.topic().trim();
        int depth = command.depth() == null ? properties.defaultDepth() : command.depth();
        int passThreshold = properties.passThreshold();
        int maxRevisions = properties.maxRevisions();

        log.info("Starting research pipeline topicLength={} depth={} engine={}",
                topic.length(), depth, properties.engine());

        long t0 = System.currentTimeMillis();
        ResearchPlan plan = planner.plan(topic, depth);
        trace.record(
                ResearchRole.PLANNER,
                "completed",
                "topic: " + topic + "\ndepth: " + depth,
                formatPlan(plan),
                System.currentTimeMillis() - t0);

        t0 = System.currentTimeMillis();
        List<Finding> findings = researcher.research(plan);
        trace.record(
                ResearchRole.RESEARCHER,
                "completed",
                formatPlan(plan),
                formatFindings(findings),
                System.currentTimeMillis() - t0);

        Critique critique = Critique.none();
        String draft = null;
        int revisions = 0;

        while (true) {
            t0 = System.currentTimeMillis();
            draft = writer.write(topic, findings, critique);
            String writerInput = """
                    topic: %s

                    findings:
                    %s

                    critique notes:
                    %s
                    """.formatted(topic, formatFindings(findings), critique.notes()).trim();
            trace.record(
                    ResearchRole.WRITER,
                    "completed",
                    writerInput,
                    draft,
                    System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            critique = critic.critique(draft);
            trace.record(
                    ResearchRole.CRITIC,
                    "completed",
                    draft,
                    "score: " + critique.score() + "\nnotes: " + critique.notes(),
                    System.currentTimeMillis() - t0);

            if (critique.passes(passThreshold) || revisions >= maxRevisions) {
                break;
            }
            revisions++;
        }

        long elapsed = System.currentTimeMillis() - started;
        log.info("Finished research pipeline elapsedMs={} criticScore={} steps={}",
                elapsed, critique.score(), trace.steps().size());

        return new ResearchReport(
                topic,
                plan,
                findings,
                draft,
                critique,
                draft,
                trace.steps(),
                properties.engine(),
                properties.chatModel(),
                elapsed
        );
    }

    private static String formatPlan(ResearchPlan plan) {
        if (plan == null || plan.questions() == null || plan.questions().isEmpty()) {
            return "(no questions)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String question : plan.questions()) {
            sb.append(i++).append(". ").append(question).append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatFindings(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "(no findings)";
        }
        return findings.stream()
                .map(f -> "Q: " + f.question() + "\nA: " + f.answer())
                .collect(Collectors.joining("\n\n"));
    }
}
