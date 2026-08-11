package dev.mytechprofile.research.langchain4j.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.research.langchain4j.domain.ResearchFindings;
import dev.mytechprofile.research.langchain4j.domain.ResearchPlan;

/**
 * Answers each planner question using the online research model.
 */
public interface ResearcherAgent {

    @UserMessage("""
            For each question in this plan, provide a concise factual answer.
            Plan: {{plan}}
            Return structured JSON with a findings array of objects that each have
            question and answer fields.
            """)
    @Agent(description = "Researches answers for planned questions", outputKey = "findingsDoc")
    ResearchFindings research(@V("plan") ResearchPlan plan);
}
