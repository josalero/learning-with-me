package dev.mytechprofile.research.springai.agents;

import java.util.List;

import dev.mytechprofile.research.springai.domain.Finding;
import dev.mytechprofile.research.springai.domain.ResearchPlan;

/**
 * Answers each planner question using the online research model.
 */
public interface ResearcherAgent {

    List<Finding> research(ResearchPlan plan);
}
