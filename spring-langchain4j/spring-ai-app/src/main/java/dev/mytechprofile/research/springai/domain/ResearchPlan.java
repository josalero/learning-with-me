package dev.mytechprofile.research.springai.domain;

import java.util.List;

/**
 * Structured planner output: research questions to investigate.
 */
public record ResearchPlan(List<String> questions) {
}
