package dev.mytechprofile.sdlc.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mytechprofile.sdlc.domain.BuildResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildGateTest {

    @Test
    void shouldSkip_whenTreeIsCleanAndTestsAlreadyRan_returnsTrue() {
        BuildResult previous = new BuildResult(true, 0, "ok", 100, false);

        assertThat(BuildGate.shouldSkip(false, previous)).isTrue();
    }

    @Test
    void shouldSkip_whenTreeIsDirty_returnsFalse() {
        BuildResult previous = new BuildResult(true, 0, "ok", 100, false);

        assertThat(BuildGate.shouldSkip(true, previous)).isFalse();
    }

    @Test
    void shouldSkip_whenTestsHaveNotRun_returnsFalse() {
        assertThat(BuildGate.shouldSkip(false, BuildResult.none())).isFalse();
    }

    @Test
    void attachExecutedTests_whenJunitXmlExists_addsMethodNames(@TempDir Path root) throws Exception {
        Path report = root.resolve("build/test-results/test/TEST-UserControllerTest.xml");
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                """
                <testsuite>
                  <testcase name="createWithBlankNameReturns400()" classname="dev.demo.users.UserControllerTest"/>
                </testsuite>
                """);
        BuildResult green = new BuildResult(true, 0, "BUILD SUCCESSFUL", 100, false);

        BuildResult attached = BuildGate.attachExecutedTests(green, root);

        assertThat(attached.executedTests()).containsExactly("UserControllerTest.createWithBlankNameReturns400");
        assertThat(attached.truncatedOutput()).contains("Executed tests:");
    }
}
