package dev.mytechprofile.jev.decide;

import java.util.Map;
import java.util.Objects;

/**
 * Typed decision. Jev does not return prose; confidence and probabilities are the result.
 *
 * <p>Built by {@link OpenRouterJevDecider} or {@link YearsSeniorityDecider} before any
 * explanation is written. Confidence must be between 0 and 1 inclusive.
 *
 * @param level chosen label
 * @param confidence 0 to 1 from Jev, or the constant {@code 0.99} offline
 * @param probabilities one entry per {@link Seniority}; missing upstream keys are {@code 0}
 * @param decider {@code jev} or {@code offline-years}
 */
public record SeniorityDecision(
        Seniority level,
        double confidence,
        Map<Seniority, Double> probabilities,
        String decider) {

    /**
     * Rejects a null level, a null decider, a null probability map, and a confidence
     * outside 0 to 1. The probability map is copied.
     */
    public SeniorityDecision {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(decider, "decider");
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        probabilities = Map.copyOf(Objects.requireNonNull(probabilities, "probabilities"));
    }
}
