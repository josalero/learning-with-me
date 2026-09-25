package dev.mytechprofile.jev.decide;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YearsSeniorityDeciderTest {

    private final YearsSeniorityDecider decider = new YearsSeniorityDecider();

    @Test
    void oneYearAfterBootcampIsJunior() {
        SeniorityDecision decision = decider.decide("""
                Intern after a bootcamp.
                1 year of professional experience.
                """);

        assertThat(decision.level()).isEqualTo(Seniority.JUNIOR);
        assertThat(decision.decider()).isEqualTo(YearsSeniorityDecider.DECIDER);
    }

    @Test
    void fourYearsOfIndependentDeliveryIsIntermediate() {
        SeniorityDecision decision = decider.decide("4 years of experience. Delivers features independently.");

        assertThat(decision.level()).isEqualTo(Seniority.INTERMEDIATE);
    }

    @Test
    void tenYearsWithArchitectureIsSenior() {
        SeniorityDecision decision = decider.decide("10 years of experience. Sets architecture and mentors engineers.");

        assertThat(decision.level()).isEqualTo(Seniority.SENIOR);
        assertThat(decision.probabilities()).containsEntry(Seniority.SENIOR, 0.99);
    }

    @Test
    void spanishYearsCountTowardTheSameRubric() {
        SeniorityDecision decision = decider.decide("8 años de experiencia liderando diseño técnico.");

        assertThat(decision.level()).isEqualTo(Seniority.SENIOR);
    }
}
