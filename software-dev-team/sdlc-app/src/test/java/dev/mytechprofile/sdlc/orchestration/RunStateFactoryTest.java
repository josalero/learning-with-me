package dev.mytechprofile.sdlc.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunStateFactoryTest {

    @Test
    void truncateDiff_whenShort_isUnchanged() {
        assertThat(RunStateFactory.truncateDiff("diff --git a/x b/x")).isEqualTo("diff --git a/x b/x");
    }

    @Test
    void truncateDiff_whenHuge_keepsAHeadAndMarksTruncation() {
        String huge = "x".repeat(RunStateFactory.MAX_GIT_DIFF_CHARS + 50);

        String truncated = RunStateFactory.truncateDiff(huge);

        assertThat(truncated.length()).isLessThan(huge.length());
        assertThat(truncated).endsWith("[diff truncated for the reviewer prompt]");
    }

    @Test
    void nextGitDiff_whenWorkingTreeIsClean_keepsThePreviousFeatureDiff() {
        assertThat(RunStateFactory.nextGitDiff("diff --git a/UserController.java", ""))
                .isEqualTo("diff --git a/UserController.java");
        assertThat(RunStateFactory.nextGitDiff("old", "diff --git a/new")).isEqualTo("diff --git a/new");
    }
}
