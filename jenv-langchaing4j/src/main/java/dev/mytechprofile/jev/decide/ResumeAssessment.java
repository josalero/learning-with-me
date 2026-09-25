package dev.mytechprofile.jev.decide;

import java.util.Map;
import java.util.Objects;

/**
 * HTTP result: the decision, the review flag, and a Spanish explanation.
 *
 * <p>The explanation does not override {@code level}. Example offline body for
 * {@code "4 years of experience."}:
 *
 * <pre>{@code
 * {
 *   "level": "INTERMEDIATE",
 *   "confidence": 0.99,
 *   "needsHumanReview": false,
 *   "decider": "offline-years"
 * }
 * }</pre>
 *
 * @param level chosen label
 * @param confidence 0 to 1 from Jev, or {@code 0.99} offline
 * @param probabilities probability of each label
 * @param needsHumanReview {@code true} when confidence is strictly below {@code app.human-review-below}
 * @param explanation Spanish paragraph; template or chat model
 * @param decider {@code jev} or {@code offline-years}
 */
public record ResumeAssessment(
        Seniority level,
        double confidence,
        Map<Seniority, Double> probabilities,
        boolean needsHumanReview,
        String explanation,
        String decider) {

    /**
     * Rejects a null level, explanation, or decider, and a confidence outside 0 to 1.
     * The probability map is copied.
     */
    public ResumeAssessment {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(decider, "decider");
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        probabilities = Map.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }
}
