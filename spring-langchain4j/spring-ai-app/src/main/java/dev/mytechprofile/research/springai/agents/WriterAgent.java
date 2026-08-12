package dev.mytechprofile.research.springai.agents;

import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.ResearchFindings;

/**
 * Writes or revises the markdown research report.
 */
public interface WriterAgent {

    String write(String topic, ResearchFindings findingsDoc, Critique critique);
}
