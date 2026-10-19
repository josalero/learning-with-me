package dev.mytechprofile.sdlc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mytechprofile.sdlc.adapter.LocalGit;
import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.RoleKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCursorDeveloperTest {

    @TempDir
    Path temp;

    @Test
    void argv_whenImplementing_usesWorkspaceAndForce_notAskMode() {
        Path workspace = temp.resolve("workspace");
        SdlcProperties properties = liveProperties("test-key");
        LocalCursorDeveloper developer =
                new LocalCursorDeveloper(properties, (argv, cwd, timeout) -> "", new LocalGit());
        RoleSpec role = developerRole();

        List<String> argv = developer.argv(role, workspace, "implement the spec");

        assertThat(argv.getFirst()).isEqualTo("agent");
        assertThat(argv)
                .contains("-p", "--force", "--trust", "--workspace", workspace.toString(), "--model", "composer-2.5");
        assertThat(argv).doesNotContain("--mode", "ask");
        assertThat(argv.getLast()).isEqualTo("implement the spec");
        assertThat(developer.implementTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void implement_whenCliEditsSource_buildsChangeSummaryFromGit() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace);
        LocalGit git = new LocalGit();
        git.initIfNeeded(workspace);
        Files.writeString(
                workspace.resolve("PingService.java"), "class PingService { String status() { return \"todo\"; } }\n");
        git.commitAll(workspace, "chore: seed");

        AtomicReference<Path> cwd = new AtomicReference<>();
        LocalCursorDeveloper developer = new LocalCursorDeveloper(
                liveProperties("test-key"),
                (argv, workingDirectory, timeout) -> {
                    cwd.set(workingDirectory);
                    try {
                        Files.writeString(
                                workspace.resolve("PingService.java"),
                                "class PingService { String status() { return \"ok\"; } }\n");
                    } catch (IOException ex) {
                        throw new IllegalStateException("Cannot edit workspace in test", ex);
                    }
                    return "updated PingService.status to ok";
                },
                git);

        ChangeSummary summary = developer.implement(
                developerRole(),
                workspace,
                new AiSpec("Return ok", List.of("PingService.java"), "", "", List.of(), List.of(), List.of()),
                FeatureBrief.fromRequest("Ping should return ok"),
                "PingService.java",
                "",
                "",
                "",
                "");

        assertThat(cwd.get()).isEqualTo(workspace.toAbsolutePath().normalize());
        assertThat(summary.filesTouched()).contains("PingService.java");
        assertThat(summary.rationale()).isEqualTo("Cursor agent implemented the spec");
        assertThat(summary.notes()).contains("updated PingService.status");
    }

    @Test
    void implement_whenApiKeyBlank_throwsActionableError() {
        LocalCursorDeveloper developer =
                new LocalCursorDeveloper(liveProperties(""), (argv, cwd, timeout) -> "nope", new LocalGit());

        assertThatThrownBy(() -> developer.implement(
                        developerRole(), temp, AiSpec.empty(), FeatureBrief.fromRequest("x"), "", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CURSOR_API_KEY");
    }

    private SdlcProperties liveProperties(String apiKey) {
        return SdlcProperties.of(
                temp,
                apiKey,
                "agent",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                false);
    }

    private static RoleSpec developerRole() {
        return new RoleSpec("developer", RoleKind.DEVELOPER, "composer-2.5", "prompts/developer.md", 0);
    }
}
