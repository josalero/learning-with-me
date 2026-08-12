package dev.mytechprofile.research.springai.agents;

import dev.mytechprofile.research.springai.domain.ResearchFindings;
import dev.mytechprofile.research.springai.domain.ResearchPlan;

/**
 * Answers each planner question using the online research model.
 */
public interface ResearcherAgent {

    ResearchFindings research(ResearchPlan plan);
}
