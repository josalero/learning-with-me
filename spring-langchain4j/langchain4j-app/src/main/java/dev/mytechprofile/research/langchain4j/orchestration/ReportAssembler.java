package dev.mytechprofile.research.langchain4j.orchestration;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.research.langchain4j.config.ResearchProperties;
import dev.mytechprofile.research.langchain4j.domain.Critique;
import dev.mytechprofile.research.langchain4j.domain.Finding;
import dev.mytechprofile.research.langchain4j.domain.ResearchFindings;
import dev.mytechprofile.research.langchain4j.domain.ResearchPlan;
import dev.mytechprofile.research.langchain4j.domain.ResearchReport;
import dev.mytechprofile.research.langchain4j.domain.StepEvent;

/**
 * Maps AgenticScope state into the shared {@link ResearchReport} contract.
 */
@Component
public class ReportAssembler {

    private final ResearchProperties properties;

    public ReportAssembler(ResearchProperties properties) {
        this.properties = properties;
    }

    public ResearchReport assemble(
            String topic,
            AgenticScope scope,
            List<StepEvent> steps,
            long startedAtMs) {
        ResearchPlan plan = scope.readState("plan", new ResearchPlan(List.of()));
        ResearchFindings findingsDoc = scope.readState("findingsDoc", new ResearchFindings(List.of()));
        List<Finding> findings = findingsDoc == null || findingsDoc.findings() == null
                ? List.of()
                : findingsDoc.findings();
        String draft = scope.readState("draft", "");
        Critique critiqueState = scope.readState("critique", Critique.none());
        Critique critique = critiqueState == null ? Critique.none() : critiqueState;

        return new ResearchReport(
                topic,
                plan,
                findings,
                draft,
                critique,
                draft,
                steps,
                properties.engine(),
                properties.chatModel(),
                System.currentTimeMillis() - startedAtMs
        );
    }
}
