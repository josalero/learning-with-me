package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewEvidenceTest {

    @Test
    void reconcile_whenReviewerSaysDiffOmittedFilesButTestsRan_approves() {
        AiSpec spec = new AiSpec(
                "done",
                List.of("src/main/java/dev/demo/users/UserController.java"),
                "",
                "",
                List.of(),
                List.of(),
                List.of(
                        new TraceabilityItem("AC-1", "UserControllerTest.unknownIdReturns404"),
                        new TraceabilityItem("AC-2", "UserControllerTest.unknownIdReturnsProblemDetail"),
                        new TraceabilityItem("AC-3", "UserControllerTest.blankNameReturns400")));
        BuildResult build = new BuildResult(true, 0, "BUILD SUCCESSFUL", 1_000, false)
                .withExecutedTests(List.of(
                        "UserControllerTest.unknownIdReturns404",
                        "UserControllerTest.unknownIdReturnsProblemDetail",
                        "UserControllerTest.blankNameReturns400"));
        ReviewVerdict llm = new ReviewVerdict(
                "REQUEST_CHANGES",
                List.of(new ReviewFinding(
                        "error",
                        "src/main/java/dev/demo/users/UserController.java",
                        "The diff does not include any changes to UserController.java.")),
                1);

        ReviewVerdict reconciled = ReviewEvidence.reconcile(llm, spec, build, ".gradle/checksums.lock");

        assertThat(reconciled.approved()).isTrue();
        assertThat(reconciled.findings()).isEmpty();
    }

    @Test
    void reconcile_whenFileIsInTheDiff_dropsTheEmptyDiffFinding() {
        ReviewVerdict llm = new ReviewVerdict(
                "REQUEST_CHANGES",
                List.of(new ReviewFinding(
                        "error",
                        "UserController.java",
                        "The diff does not include any changes to UserController.java.")),
                1);

        ReviewVerdict reconciled = ReviewEvidence.reconcile(
                llm, AiSpec.empty(), BuildResult.none(), "diff --git a/UserController.java b/UserController.java");

        assertThat(reconciled.approved()).isTrue();
    }
}
