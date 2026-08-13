package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewVerdictTest {

    @Test
    void compactConstructor_whenRequestChangesHasNoBlockingFindings_becomesApprove() {
        ReviewVerdict verdict = new ReviewVerdict(
                "REQUEST_CHANGES", List.of(new ReviewFinding("info", "UserController.java", "nit")), 2);

        assertThat(verdict.approved()).isTrue();
        assertThat(verdict.blockingCount()).isZero();
    }

    @Test
    void compactConstructor_whenErrorFindingPresent_staysRequestChanges() {
        ReviewVerdict verdict = new ReviewVerdict(
                "APPROVE", List.of(new ReviewFinding("error", "UserController.java", "missing test")), 0);

        assertThat(verdict.requestsChanges()).isTrue();
        assertThat(verdict.blockingCount()).isEqualTo(1);
    }
}
