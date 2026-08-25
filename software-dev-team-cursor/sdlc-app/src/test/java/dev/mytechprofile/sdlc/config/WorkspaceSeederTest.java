package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mytechprofile.sdlc.adapter.LocalGit;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSeederTest {

    @TempDir
    Path home;

    @Test
    void resetWorkspace_replacesEditedFilesWithTheSeedCopy() throws Exception {
        Path seed = home.resolve("seeds/echo");
        Path repo = home.resolve("workspace/echo");
        Files.createDirectories(seed);
        Files.writeString(seed.resolve("README.md"), "seed copy");
        ProjectProfile profile = new ProjectProfile(
                "echo",
                seed,
                repo,
                "feature/",
                List.of("**/*"),
                null,
                Map.of("test", List.of("/bin/echo", "ok")),
                Duration.ofSeconds(30),
                null);
        WorkspaceSeeder seeder = new WorkspaceSeeder(new LocalGit());

        seeder.ensureWorkspace(profile);
        Files.writeString(repo.resolve("README.md"), "agent edited this");
        Files.writeString(repo.resolve("extra.txt"), "leftover");

        seeder.resetWorkspace(profile);

        assertThat(repo.resolve("README.md")).hasContent("seed copy");
        assertThat(repo.resolve("extra.txt")).doesNotExist();
        assertThat(repo.resolve(".git")).isDirectory();
    }
}
