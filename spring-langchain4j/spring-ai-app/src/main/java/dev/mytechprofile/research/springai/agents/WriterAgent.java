package dev.mytechprofile.research.springai.agents;

import java.util.List;

import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.Finding;

/**
 * Writes or revises the markdown research report.
 */
public interface WriterAgent {

    String write(String topic, List<Finding> findings, Critique previousCritique);
}
