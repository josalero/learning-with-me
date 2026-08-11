package dev.mytechprofile.research.langchain4j.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.research.langchain4j.domain.Critique;

/**
 * Scores a draft and returns revision notes.
 */
public interface CriticAgent {

    @UserMessage("""
            Draft to review:
            {{draft}}
            Return structured JSON with fields score and notes.
            """)
    @Agent(description = "Critiques a draft report", outputKey = "critique")
    Critique critique(@V("draft") String draft);
}
