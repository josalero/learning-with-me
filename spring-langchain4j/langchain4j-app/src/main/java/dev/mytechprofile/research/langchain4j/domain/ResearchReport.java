package dev.mytechprofile.research.langchain4j.domain;

import java.util.List;

/**
 * Full research pipeline result returned by {@code POST /api/v1/research}.
 */
public record ResearchReport(
        String topic,
        ResearchPlan plan,
        List<Finding> findings,
        String draft,
        Critique critique,
        String finalReport,
        List<StepEvent> steps,
        String engine,
        String model,
        long elapsedMs
) {
}
