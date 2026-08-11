package dev.mytechprofile.research.langchain4j.agents;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.research.langchain4j.domain.Critique;
import dev.mytechprofile.research.langchain4j.domain.ResearchFindings;

/**
 * Writes or revises the markdown research report.
 */
public interface WriterAgent {

    @UserMessage("""
            Topic: {{topic}}
            Findings: {{findingsDoc}}
            Critique notes to address: {{critique}}
            If critique notes exist, revise the prior draft accordingly.
            Return only the markdown report.
            """)
    @Agent(description = "Writes or revises the research report", outputKey = "draft")
    String write(
            @V("topic") String topic,
            @V("findingsDoc") ResearchFindings findingsDoc,
            @V("critique") Critique critique);
}
