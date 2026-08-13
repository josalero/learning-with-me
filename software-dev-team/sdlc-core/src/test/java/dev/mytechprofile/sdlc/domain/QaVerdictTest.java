package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QaVerdictTest {

    @Test
    void compactConstructor_whenEveryResultPassedButScoreIsZero_becomesPass100() {
        QaVerdict verdict = new QaVerdict(
                "FAIL",
                0,
                List.of(
                        new QaResult("AC-1", "PASS", "UserControllerTest.unknownIdReturns404"),
                        new QaResult("AC-2", "PASS", "UserControllerTest.unknownIdReturnsProblemDetail")),
                List.of());

        assertThat(verdict.decision()).isEqualTo(QaVerdict.PASS);
        assertThat(verdict.score()).isEqualTo(100);
        assertThat(verdict.passed(80)).isTrue();
    }

    @Test
    void compactConstructor_whenOneResultFailed_staysFailAndScoresPercentage() {
        QaVerdict verdict = new QaVerdict(
                "PASS",
                100,
                List.of(new QaResult("AC-1", "PASS", "ok"), new QaResult("AC-2", "FAIL", "missing")),
                List.of());

        assertThat(verdict.decision()).isEqualTo(QaVerdict.FAIL);
        assertThat(verdict.score()).isEqualTo(50);
        assertThat(verdict.failed(80)).isTrue();
    }

    @Test
    void compactConstructor_whenMissingTests_forcesFail() {
        QaVerdict verdict = new QaVerdict("PASS", 100, List.of(), List.of("unknownIdReturns404"));

        assertThat(verdict.decision()).isEqualTo(QaVerdict.FAIL);
        assertThat(verdict.failed(80)).isTrue();
    }
}
