package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mytechprofile.sdlc.catalog.CatalogException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogLoaderTest {

    @TempDir
    Path home;

    @Test
    void loadTeams_whenKindIsUnknown_listsTheAllowedKinds() throws Exception {
        TestHomes.write(home.resolve("prompts/developer.md"), "dev");
        TestHomes.write(
                home.resolve("config/teams/bad.yaml"),
                """
                id: bad
                roles:
                  - id: wizard
                    kind: WIZARD
                    model: fast
                    prompt: prompts/developer.md
                    temperature: 0
                policy:
                  maxSpecRework: 1
                  maxImplementationAttempts: 1
                  maxReviewCycles: 1
                  maxQaCycles: 1
                  maxStakeholderCycles: 1
                  qaPassThreshold: 80
                """);

        CatalogLoader loader = new CatalogLoader(TestHomes.properties(home));

        assertThatThrownBy(loader::loadTeams)
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("1.")
                .hasMessageContaining("Unknown role kind 'WIZARD'")
                .hasMessageContaining("DEVELOPER");
    }

    @Test
    void loadTeams_whenPromptIsMissing_mentionsThePath() throws Exception {
        TestHomes.write(
                home.resolve("config/teams/bad.yaml"),
                """
                id: bad
                roles:
                  - id: developer
                    kind: DEVELOPER
                    model: fast
                    prompt: prompts/missing.md
                    temperature: 0
                policy:
                  maxSpecRework: 1
                  maxImplementationAttempts: 1
                  maxReviewCycles: 1
                  maxQaCycles: 1
                  maxStakeholderCycles: 1
                  qaPassThreshold: 80
                """);

        CatalogLoader loader = new CatalogLoader(TestHomes.properties(home));

        assertThatThrownBy(loader::loadTeams)
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("Prompt file for role developer is missing");
    }

    @Test
    void loadProjects_whenRepoPathEscapesWorkspace_fails() throws Exception {
        Files.createDirectories(home.resolve("seeds/demo"));
        TestHomes.write(
                home.resolve("config/projects/escape.yaml"),
                """
                id: escape
                seed: seeds/demo
                repoPath: /tmp/outside
                commands:
                  test: ["true"]
                """);

        CatalogLoader loader = new CatalogLoader(TestHomes.properties(home));

        assertThatThrownBy(loader::loadProjects)
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("repoPath must stay under");
    }

    @Test
    void loadTeams_whenTwoFilesAreInvalid_returnsAShoppingList() throws Exception {
        TestHomes.write(home.resolve("prompts/developer.md"), "dev");
        TestHomes.write(
                home.resolve("config/teams/one.yaml"),
                """
                id: one
                roles:
                  - id: a
                    kind: NOPE
                    model: fast
                    prompt: prompts/developer.md
                policy:
                  maxSpecRework: 1
                  maxImplementationAttempts: 1
                  maxReviewCycles: 1
                  maxQaCycles: 1
                  maxStakeholderCycles: 1
                  qaPassThreshold: 80
                """);
        TestHomes.write(
                home.resolve("config/teams/two.yaml"),
                """
                id: two
                roles:
                  - id: b
                    kind: DEVELOPER
                    model: fast
                    prompt: prompts/gone.md
                policy:
                  maxSpecRework: 1
                  maxImplementationAttempts: 1
                  maxReviewCycles: 1
                  maxQaCycles: 1
                  maxStakeholderCycles: 1
                  qaPassThreshold: 80
                """);

        CatalogLoader loader = new CatalogLoader(TestHomes.properties(home));

        assertThatThrownBy(loader::loadTeams)
                .isInstanceOf(CatalogException.class)
                .hasMessageContaining("1.")
                .hasMessageContaining("2.")
                .hasMessageContaining("NOPE")
                .hasMessageContaining("gone.md");
        assertThat(home.resolve("config/teams")).isDirectory();
    }
}
