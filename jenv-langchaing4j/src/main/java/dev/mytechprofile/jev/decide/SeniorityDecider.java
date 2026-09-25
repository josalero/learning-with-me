package dev.mytechprofile.jev.decide;

/**
 * Turns resume text into a seniority decision.
 *
 * <p>The live adapter calls Jev. The offline adapter counts years. Callers pass
 * non-blank text; this interface does not validate the string.
 */
public interface SeniorityDecider {

    /**
     * Classifies one resume.
     *
     * @param resumeText plain text already checked for blankness by the service
     * @return label, confidence, probabilities, and decider id
     * @throws JevClientException when the live call fails or the choice is not a known label
     */
    SeniorityDecision decide(String resumeText);
}
