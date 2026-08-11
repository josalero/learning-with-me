package dev.mytechprofile.research.langchain4j.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.research.langchain4j.domain.ResearchPlan;

/**
 * Decomposes a topic into focused research questions.
 */
public interface PlannerAgent {

    @UserMessage("""
            Topic: {{topic}}
            Depth: {{depth}}
            Produce {{depth}} research questions.
            """)
    @Agent(description = "Plans research questions for a topic", outputKey = "plan")
    ResearchPlan plan(@V("topic") String topic, @V("depth") int depth);
}
