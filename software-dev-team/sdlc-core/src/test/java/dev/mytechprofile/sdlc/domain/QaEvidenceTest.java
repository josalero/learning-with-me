package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaEvidenceTest {

    @Test
    void reconcile_whenLlmFailsAcWhoseTestRan_passesTheCriterion() {
        FeatureBrief brief = new FeatureBrief(
                "404 and blank name",
                "gaps",
                List.of(),
                List.of(
                        new AcceptanceCriterion("AC-1", "unknown id", "GET", "404"),
                        new AcceptanceCriterion("AC-2", "unknown id", "GET", "problem"),
                        new AcceptanceCriterion("AC-3", "blank name", "POST", "400")),
                List.of(),
                "must");
        AiSpec spec = new AiSpec(
                "done",
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                List.of(
                        new TraceabilityItem("AC-1", "UserControllerTest.unknownIdReturns404ProblemDetail"),
                        new TraceabilityItem("AC-2", "UserControllerTest.unknownIdReturns404ProblemDetail"),
                        new TraceabilityItem("AC-3", "UserControllerTest.createWithBlankNameReturns400")));
        BuildResult build = new BuildResult(true, 0, "BUILD SUCCESSFUL", 5_000, false)
                .withExecutedTests(List.of(
                        "UserControllerTest.unknownIdReturns404ProblemDetail",
                        "UserControllerTest.createWithBlankNameReturns400"));
        QaVerdict llm = new QaVerdict(
                "FAIL",
                67,
                List.of(
                        new QaResult("AC-1", "PASS", "UserControllerTest.unknownIdReturns404ProblemDetail"),
                        new QaResult("AC-2", "PASS", "UserControllerTest.unknownIdReturns404ProblemDetail"),
                        new QaResult("AC-3", "FAIL", "")),
                List.of("UserControllerTest.createWithBlankNameReturns400"));

        QaVerdict reconciled = QaEvidence.reconcile(llm, brief, spec, build);

        assertThat(reconciled.decision()).isEqualTo(QaVerdict.PASS);
        assertThat(reconciled.score()).isEqualTo(100);
        assertThat(reconciled.missingTests()).isEmpty();
        assertThat(reconciled.passed(80)).isTrue();
    }

    @Test
    void reconcile_whenBuildFailed_doesNotUpgradeFailRows() {
        FeatureBrief brief = FeatureBrief.fromRequest("404");
        AiSpec spec = new AiSpec(
                "",
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                List.of(new TraceabilityItem("AC-1", "UserControllerTest.unknownIdReturns404")));
        BuildResult build = new BuildResult(false, 1, "BUILD FAILED", 1_000, false)
                .withExecutedTests(List.of("UserControllerTest.unknownIdReturns404"));
        QaVerdict llm = new QaVerdict("FAIL", 0, List.of(new QaResult("AC-1", "FAIL", "red")), List.of());

        QaVerdict reconciled = QaEvidence.reconcile(llm, brief, spec, build);

        assertThat(reconciled.decision()).isEqualTo(QaVerdict.FAIL);
        assertThat(reconciled.passed(80)).isFalse();
    }

    @Test
    void ran_whenJunitXmlUsesParentheses_stillMatchesPlannedName() {
        BuildResult build = new BuildResult(true, 0, "", 10, false)
                .withExecutedTests(List.of("dev.demo.users.UserControllerTest.createWithBlankNameReturns400()"));

        assertThat(QaEvidence.ran("UserControllerTest.createWithBlankNameReturns400", build))
                .isTrue();
    }
}
