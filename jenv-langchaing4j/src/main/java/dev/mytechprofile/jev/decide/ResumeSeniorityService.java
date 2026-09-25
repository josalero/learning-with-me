package dev.mytechprofile.jev.decide;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import dev.mytechprofile.jev.config.AppProperties;
import dev.mytechprofile.jev.explain.SeniorityExplainer;

/**
 * Jev (or the offline year rubric) chooses the level, then the explainer writes the paragraph.
 */
@Service
public class ResumeSeniorityService {

    private final SeniorityDecider decider;
    private final SeniorityExplainer explainer;
    private final double humanReviewBelow;

    /**
     * @param decider Jev when a key is set, otherwise the year rubric
     * @param explainer chat model or the Spanish template
     * @param properties supplies {@code humanReviewBelow}; {@code demo} is ignored here
     */
    public ResumeSeniorityService(
            SeniorityDecider decider,
            SeniorityExplainer explainer,
            AppProperties properties) {
        this.decider = decider;
        this.explainer = explainer;
        this.humanReviewBelow = properties.humanReviewBelow();
    }

    /**
     * Classifies non-blank resume text no larger than 64KB.
     * The same stripped text is passed to the decider and the explainer.
     * A confidence below {@code app.human-review-below} sets {@code needsHumanReview}.
     * Example: a resume that says "1 year" and "bootcamp" is junior when the offline decider is active.
     *
     * @param resumeText pasted resume; leading and trailing whitespace is removed before classification
     * @return decision, review flag, and explanation
     * @throws InvalidResumeException when the text is blank or its UTF-8 size exceeds {@code ResumeFiles.MAX_BYTES}
     */
    public ResumeAssessment assess(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new InvalidResumeException("Resume text is empty");
        }
        String text = resumeText.strip();
        if (text.getBytes(StandardCharsets.UTF_8).length > ResumeFiles.MAX_BYTES) {
            throw new InvalidResumeException("Resume text is larger than 64KB");
        }
        SeniorityDecision decision = decider.decide(text);
        boolean needsHumanReview = decision.confidence() < humanReviewBelow;
        String explanation = explainer.explain(text, decision);
        return new ResumeAssessment(
                decision.level(),
                decision.confidence(),
                decision.probabilities(),
                needsHumanReview,
                explanation,
                decision.decider());
    }
}
