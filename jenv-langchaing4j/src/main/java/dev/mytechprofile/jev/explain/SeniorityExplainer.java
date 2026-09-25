package dev.mytechprofile.jev.explain;

import dev.mytechprofile.jev.decide.SeniorityDecision;

/**
 * Writes a short justification after the level is already chosen.
 * The explainer must not change the decision.
 */
public interface SeniorityExplainer {

    /**
     * Writes the Spanish paragraph for a decision that is already chosen.
     *
     * <p>Example: offline text {@code "4 years of experience."} and level
     * {@code INTERMEDIATE} produces a sentence that starts with
     * {@code Perfil intermedio (4 años)}.
     *
     * @param resumeText same stripped text the decider saw
     * @param decision label and confidence; this method must not replace {@code level}
     * @return one or two Spanish sentences
     */
    String explain(String resumeText, SeniorityDecision decision);
}
