package dev.mytechprofile.research.springai.agents;

import dev.mytechprofile.research.springai.domain.Critique;

/**
 * Scores a draft and returns revision notes.
 */
public interface CriticAgent {

    Critique critique(String draft);
}
