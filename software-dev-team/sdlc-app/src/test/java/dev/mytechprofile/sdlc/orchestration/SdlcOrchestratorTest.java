package dev.mytechprofile.sdlc.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mytechprofile.sdlc.adapter.FileArtifactStore;
import dev.mytechprofile.sdlc.adapter.InMemoryRunStore;
import dev.mytechprofile.sdlc.adapter.LocalGit;
import dev.mytechprofile.sdlc.adapter.ProcessCommandRunner;
import dev.mytechprofile.sdlc.config.CatalogLoader;
import dev.mytechprofile.sdlc.config.PromptLoader;
import dev.mytechprofile.sdlc.config.RepoContextAssembler;
import dev.mytechprofile.sdlc.config.ScriptedChatModel;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.config.WorkspaceSeeder;
import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdlcOrchestratorTest {

    @TempDir
    Path home;

    @Test
    void run_withScriptedModelAndAllowlistedTrue_completesDeveloperOnlyTeam() throws Exception {
        write(home.resolve("prompts/developer.md"), "Implement the spec.");
        Files.createDirectories(home.resolve("seeds/echo"));
        Files.writeString(home.resolve("seeds/echo/README.md"), "echo seed");
        write(
                home.resolve("config/teams/dev-only.yaml"),
                """
                id: dev-only
                roles:
                  - id: developer
                    kind: DEVELOPER
                    model: offline
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
        write(
                home.resolve("config/projects/echo.yaml"),
                """
                id: echo
                seed: seeds/echo
                repoPath: workspace/echo
                commands:
                  test: ["/bin/echo", "ok"]
                timeoutSeconds: 30
                """);

        SdlcProperties properties = new SdlcProperties(
                home,
                "",
                "https://openrouter.ai/api/v1",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                true);
        CatalogLoader catalogs = new CatalogLoader(properties);
        InMemoryRunStore runs = new InMemoryRunStore();
        FileArtifactStore artifacts = new FileArtifactStore(properties.runsDir());
        LocalGit git = new LocalGit();
        SdlcOrchestrator orchestrator = new SdlcOrchestrator(
                catalogs,
                role -> ScriptedChatModel.INSTANCE,
                new PromptLoader(home),
                new WorkspaceSeeder(git),
                new RepoContextAssembler(),
                new ProcessCommandRunner(properties.gradleUserHome()),
                git,
                artifacts,
                runs,
                new HumanApprovalGate(),
                properties);

        RunCommand command = new RunCommand("dev-only", "echo", "Return 404 for unknown users");
        RunOutcome pending = RunOutcome.pending("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", command);
        runs.save(pending);

        RunOutcome outcome = orchestrator.run(pending.runId(), command, event -> {});

        assertThat(outcome.status()).isIn(RunStatus.COMPLETED, RunStatus.ESCALATED);
        assertThat(outcome.buildResult().success()).isTrue();
        assertThat(artifacts.listFiles(pending.runId())).contains("04-build-result.json", "run.json");
        assertThat(Files.isDirectory(home.resolve("workspace/echo"))).isTrue();
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
