package dev.mytechprofile.sdlc.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitTest {

    @TempDir
    Path temp;

    @Test
    void workingTreeDiff_includesUntrackedNewFiles() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        LocalGit git = new LocalGit();
        git.initIfNeeded(repo);
        Files.writeString(repo.resolve("README.md"), "seed\n");
        git.commitAll(repo, "chore: seed");

        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/ProblemDetail.java"), "public record ProblemDetail(String type) {}\n");

        String diff = git.workingTreeDiff(repo);

        assertThat(diff).contains("ProblemDetail.java").contains("public record ProblemDetail");
    }

    @Test
    void workingTreeDiff_includesEditsToTrackedFiles() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        LocalGit git = new LocalGit();
        git.initIfNeeded(repo);
        Files.writeString(repo.resolve("UserController.java"), "class UserController {}\n");
        git.commitAll(repo, "chore: seed");
        Files.writeString(repo.resolve("UserController.java"), "class UserController { int x; }\n");

        String diff = git.workingTreeDiff(repo);

        assertThat(diff).contains("UserController.java").contains("int x");
    }

    @Test
    void workingTreeDiff_omitsGradleBuildOutputSoTheReviewerSeesSource() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo.resolve("src"));
        LocalGit git = new LocalGit();
        git.initIfNeeded(repo);
        Files.writeString(repo.resolve("src/UserController.java"), "class UserController {}\n");
        git.commitAll(repo, "chore: seed");

        Files.writeString(repo.resolve("src/UserController.java"), "class UserController { int x; }\n");
        Path report = repo.resolve("build/reports/tests/test/index.html");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<html>" + "noise".repeat(5_000) + "</html>\n");
        Path lock = repo.resolve(".gradle/9.4.1/checksums.lock");
        Files.createDirectories(lock.getParent());
        Files.writeString(lock, "lock\n");

        String diff = git.workingTreeDiff(repo);

        assertThat(diff).contains("UserController.java").contains("int x");
        assertThat(diff).doesNotContain("checksums.lock").doesNotContain("index.html");
        assertThat(git.hasChanges(repo)).isTrue();
    }

    @Test
    void commitAll_whenBuildOutputExists_doesNotFailBecausePathsAreIgnored() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo.resolve("src"));
        LocalGit git = new LocalGit();
        git.initIfNeeded(repo);
        Files.writeString(repo.resolve("src/UserController.java"), "class UserController {}\n");
        git.commitAll(repo, "chore: seed");

        Files.writeString(repo.resolve("src/UserController.java"), "class UserController { int x; }\n");
        Path report = repo.resolve("build/reports/tests/test/index.html");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<html>ok</html>\n");

        git.commitAll(repo, "feat: controller");

        String committed = git.diffAgainst(repo, "HEAD~1");
        assertThat(committed).contains("UserController.java");
        assertThat(committed).doesNotContain("index.html");
    }
}
