package dev.mytechprofile.jev.decide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.jev.config.AppProperties;
import dev.mytechprofile.jev.explain.SeniorityExplainer;
import dev.mytechprofile.jev.explain.TemplateExplainer;

class ResumeSeniorityServiceTest {

    @Test
    void lowConfidenceRequiresHumanReview() {
        ResumeSeniorityService service = service(resume -> new SeniorityDecision(
                Seniority.SENIOR,
                0.42,
                Map.of(Seniority.JUNIOR, 0.1, Seniority.INTERMEDIATE, 0.48, Seniority.SENIOR, 0.42),
                "jev"));

        ResumeAssessment assessment = service.assess("10 years of experience.");

        assertThat(assessment.level()).isEqualTo(Seniority.SENIOR);
        assertThat(assessment.needsHumanReview()).isTrue();
        assertThat(assessment.decider()).isEqualTo("jev");
    }

    @Test
    void highConfidenceDoesNotRequireHumanReview() {
        ResumeSeniorityService service = service(new YearsSeniorityDecider());

        ResumeAssessment assessment = service.assess("4 years of experience.");

        assertThat(assessment.level()).isEqualTo(Seniority.INTERMEDIATE);
        assertThat(assessment.needsHumanReview()).isFalse();
        assertThat(assessment.explanation()).contains("intermedio");
    }

    @Test
    void blankResumeIsRejected() {
        ResumeSeniorityService service = service(new YearsSeniorityDecider());

        assertThatThrownBy(() -> service.assess("   "))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("Resume text is empty");
    }

    @Test
    void pastedTextOver64KbIsRejected() {
        ResumeSeniorityService service = service(new YearsSeniorityDecider());
        String oversized = "a".repeat((int) ResumeFiles.MAX_BYTES + 1);

        assertThatThrownBy(() -> service.assess(oversized))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("Resume text is larger than 64KB");
    }

    @Test
    void explainerReceivesStrippedResumeText() {
        StringBuilder seen = new StringBuilder();
        SeniorityExplainer explainer = (resume, decision) -> {
            seen.append(resume);
            return "ok";
        };
        ResumeSeniorityService service = new ResumeSeniorityService(
                new YearsSeniorityDecider(),
                explainer,
                new AppProperties(false, 0.70));

        ResumeAssessment assessment = service.assess("  4 years of experience. \n");

        assertThat(seen.toString()).isEqualTo("4 years of experience.");
        assertThat(assessment.explanation()).isEqualTo("ok");
    }

    private static ResumeSeniorityService service(SeniorityDecider decider) {
        return new ResumeSeniorityService(decider, new TemplateExplainer(), new AppProperties(false, 0.70));
    }
}
