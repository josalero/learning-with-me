package dev.mytechprofile.jev.decide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.jev.explain.TemplateExplainer;

class SampleResumesTest {

    private final YearsSeniorityDecider decider = new YearsSeniorityDecider();
    private final TemplateExplainer explainer = new TemplateExplainer();

    @Test
    void classpathSamplesCoverLevelAndExplanationEdges() {
        Map<String, Expected> expected = Map.of(
                "junior.txt", new Expected(Seniority.JUNIOR, "1 año"),
                "intermediate.txt", new Expected(Seniority.INTERMEDIATE, "4 años"),
                "senior.txt", new Expected(Seniority.SENIOR, "10 años"),
                "no-years.txt", new Expected(Seniority.JUNIOR, "sin años explícitos"),
                "three-years.txt", new Expected(Seniority.INTERMEDIATE, "3 años"),
                "seven-years.txt", new Expected(Seniority.SENIOR, "7 años"),
                "mixed-years.txt", new Expected(Seniority.SENIOR, "9 años"),
                "cinco-anos.txt", new Expected(Seniority.INTERMEDIATE, "5 años"));

        assertThat(SampleResumes.all()).extracting(SampleResumes.Sample::name)
                .containsExactly(
                        "junior.txt",
                        "intermediate.txt",
                        "senior.txt",
                        "no-years.txt",
                        "three-years.txt",
                        "seven-years.txt",
                        "mixed-years.txt",
                        "cinco-anos.txt");

        for (SampleResumes.Sample sample : SampleResumes.all()) {
            SeniorityDecision decision = decider.decide(sample.text());
            Expected outcome = expected.get(sample.name());

            assertThat(decision.level()).as(sample.name()).isEqualTo(outcome.level());
            assertThat(decision.confidence()).as(sample.name()).isEqualTo(0.99);
            assertThat(decision.probabilities()).as(sample.name()).containsEntry(outcome.level(), 0.99);
            assertThat(decision.decider()).as(sample.name()).isEqualTo("offline-years");
            assertThat(explainer.explain(sample.text(), decision)).as(sample.name()).contains(outcome.explanation());
            assertThat(decision.confidence() < 0.70).as(sample.name()).isFalse();
        }
    }

    private record Expected(Seniority level, String explanation) {
    }
}
