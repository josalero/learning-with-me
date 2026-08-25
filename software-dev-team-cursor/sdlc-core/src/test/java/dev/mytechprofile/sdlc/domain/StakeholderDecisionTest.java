package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StakeholderDecisionTest {

    @Test
    void endsCycle_whenRejectedWithNoFollowUps_stopsTheLoopWithoutApproving() {
        StakeholderDecision decision = new StakeholderDecision("REJECTED", List.of(), List.of());

        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.endsCycle()).isTrue();
    }

    @Test
    void endsCycle_whenRejectedWithFollowUps_allowsAnotherCycle() {
        StakeholderDecision decision =
                new StakeholderDecision("REJECTED", List.of("AC-2 missing"), List.of("Reject blank names"));

        assertThat(decision.isApproved()).isFalse();
        assertThat(decision.endsCycle()).isFalse();
        assertThat(decision.followUps()).containsExactly("Reject blank names");
    }

    @Test
    void endsCycle_whenApproved_isTrue() {
        assertThat(StakeholderDecision.approved().endsCycle()).isTrue();
        assertThat(StakeholderDecision.approved().isApproved()).isTrue();
    }

    @Test
    void reconcile_whenQaPassed_approvesEvenIfModelRejected() {
        QaVerdict qa = QaVerdict.pass(100);
        StakeholderDecision raw = new StakeholderDecision(
                "REJECTED", List.of("AC-3"), List.of("Ensure UserControllerTest.createWithBlankNameReturns400 ran"));

        StakeholderDecision decided = StakeholderDecision.reconcile(raw, qa, 80, BuildResult.none(), AiSpec.empty());

        assertThat(decided.isApproved()).isTrue();
        assertThat(decided.endsCycle()).isTrue();
    }

    @Test
    void reconcile_whenFollowUpOnlyAsksToRerunAnExecutedTest_approves() {
        AiSpec spec = new AiSpec(
                "",
                List.of(),
                "",
                "",
                List.of(),
                List.of(),
                List.of(new TraceabilityItem("AC-3", "UserControllerTest.createWithBlankNameReturns400")));
        BuildResult build = new BuildResult(true, 0, "BUILD SUCCESSFUL", 1_000, false)
                .withExecutedTests(List.of("UserControllerTest.createWithBlankNameReturns400"));
        QaVerdict qa = new QaVerdict(
                "FAIL",
                67,
                List.of(new QaResult("AC-3", "FAIL", "")),
                List.of("UserControllerTest.createWithBlankNameReturns400"));
        StakeholderDecision raw = new StakeholderDecision(
                "REJECTED",
                List.of("AC-3"),
                List.of(
                        "Ensure that the test UserControllerTest.createWithBlankNameReturns400 is executed and passing."));

        StakeholderDecision decided = StakeholderDecision.reconcile(raw, qa, 80, build, spec);

        assertThat(decided.isApproved()).isTrue();
    }
}
