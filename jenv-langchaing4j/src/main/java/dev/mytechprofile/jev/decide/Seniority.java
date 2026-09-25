package dev.mytechprofile.jev.decide;

/**
 * Closed set of labels for the {@code seniority} choice question.
 *
 * <p>Jev criteria keys and the offline rubric both use these names.
 * {@code JUNIOR} is under 3 years, {@code INTERMEDIATE} is 3 to 6,
 * {@code SENIOR} is 7 or more. See {@code docs/rubric.md}.
 */
public enum Seniority {
    JUNIOR,
    INTERMEDIATE,
    SENIOR
}
