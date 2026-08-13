package dev.mytechprofile.sdlc.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.domain.CommandResult;
import dev.mytechprofile.sdlc.port.CommandNotAllowedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessCommandRunnerTest {

    @TempDir
    Path temp;

    @Test
    void run_whenCommandNameIsNotAllowlisted_throws() throws Exception {
        ProjectProfile profile = profile();
        ProcessCommandRunner runner = new ProcessCommandRunner(temp.resolve("gradle-home"));

        assertThatThrownBy(() -> runner.run(profile, profile.repoPath(), "deploy"))
                .isInstanceOf(CommandNotAllowedException.class)
                .hasMessageContaining("not allowlisted")
                .hasMessageContaining("deploy");
    }

    @Test
    void run_whenArgvIsNotAllowlisted_throws() throws Exception {
        ProjectProfile profile = profile();
        ProcessCommandRunner runner = new ProcessCommandRunner(temp.resolve("gradle-home"));

        assertThatThrownBy(() -> runner.run(profile, profile.repoPath(), List.of("rm", "-rf", "/")))
                .isInstanceOf(CommandNotAllowedException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void run_whenArgvIsAllowlisted_returnsExitZero() throws Exception {
        ProjectProfile profile = profile();
        ProcessCommandRunner runner = new ProcessCommandRunner(temp.resolve("gradle-home"));

        CommandResult result = runner.run(profile, profile.repoPath(), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("ok");
    }

    @Test
    void run_whenChildHoldsStdoutOpenPastTheTimeout_killsItAndReportsTimeout() throws Exception {
        ProjectProfile profile =
                profile(Map.of("test", List.of("/bin/sh", "-c", "echo starting; sleep 60")), Duration.ofSeconds(2));
        ProcessCommandRunner runner = new ProcessCommandRunner(temp.resolve("gradle-home"));

        long started = System.currentTimeMillis();
        CommandResult result = runner.run(profile, profile.repoPath(), "test");
        long elapsed = System.currentTimeMillis() - started;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("starting").contains("Timed out after");
        assertThat(elapsed).isLessThan(30_000L);
    }

    private ProjectProfile profile() throws Exception {
        return profile(Map.of("test", List.of("/bin/echo", "ok")), Duration.ofSeconds(10));
    }

    private ProjectProfile profile(Map<String, List<String>> commands, Duration timeout) throws Exception {
        Path seed = Files.createDirectories(temp.resolve("seed"));
        Path repo = Files.createDirectories(temp.resolve("workspace").resolve("demo"));
        return new ProjectProfile("demo", seed, repo, "feature/", List.of("**/*"), null, commands, timeout, null);
    }
}
