package dev.mytechprofile.jev.explain;

import dev.mytechprofile.jev.decide.SeniorityDecision;
import dev.mytechprofile.jev.decide.YearsSeniorityDecider;

/**
 * Explanation used when there is no chat-model key. It restates the year rubric.
 */
public final class TemplateExplainer implements SeniorityExplainer {

    /**
     * Restates the year rubric in Spanish. Does not call a model.
     *
     * <p>Used when the API key is blank or {@code openrouter.explain} is false,
     * and as the fallback when the chat model fails.
     * {@code "10 years of experience."} with {@code SENIOR} starts with
     * {@code Perfil senior (10 años)}.
     *
     * @param resumeText scanned only for the year phrase
     * @param decision level already chosen; a mismatch with the year count is still cited as given
     * @return one Spanish sentence
     */
    @Override
    public String explain(String resumeText, SeniorityDecision decision) {
        return YearsSeniorityDecider.rubricSentence(resumeText, decision.level());
    }
}
