package dev.mytechprofile.research.springai.domain;

/**
 * Critic score and revision notes for a draft report.
 */
public record Critique(int score, String notes) {

    public static Critique none() {
        return new Critique(0, "None — write the first draft.");
    }

    public boolean passes(int threshold) {
        return score >= threshold;
    }
}
