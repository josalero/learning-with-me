package dev.mytechprofile.research.springai.agents;

import dev.mytechprofile.research.springai.domain.ResearchPlan;

/**
 * Decomposes a topic into focused research questions.
 */
public interface PlannerAgent {

    ResearchPlan plan(String topic, int depth);
}
