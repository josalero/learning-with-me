package dev.mytechprofile.research.langchain4j.domain;

import java.util.List;

/**
 * Structured planner output: research questions to investigate.
 */
public record ResearchPlan(List<String> questions) {
}
