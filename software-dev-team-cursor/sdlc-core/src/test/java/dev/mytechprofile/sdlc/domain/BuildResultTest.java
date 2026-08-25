package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildResultTest {

    @Test
    void evaluated_whenNone_returnsFalse() {
        assertThat(BuildResult.none().evaluated()).isFalse();
    }

    @Test
    void evaluated_whenCommandRan_returnsTrue() {
        assertThat(new BuildResult(true, 0, "BUILD SUCCESSFUL", 8_000, false).evaluated())
                .isTrue();
    }

    @Test
    void withExecutedTests_appendsCatalogOnce() {
        BuildResult result = new BuildResult(true, 0, "BUILD SUCCESSFUL", 8_000, false)
                .withExecutedTests(List.of("UserControllerTest.unknownIdReturns404"));

        assertThat(result.executedTests()).containsExactly("UserControllerTest.unknownIdReturns404");
        assertThat(result.truncatedOutput()).contains("Executed tests:", "UserControllerTest.unknownIdReturns404");
        assertThat(result.withExecutedTests(List.of("UserControllerTest.unknownIdReturns404"))
                        .truncatedOutput()
                        .split("Executed tests:"))
                .hasSize(2);
    }
}
