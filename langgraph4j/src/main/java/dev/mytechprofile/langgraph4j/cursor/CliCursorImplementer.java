package dev.mytechprofile.langgraph4j.cursor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

/**
 * Local Cursor {@code agent} CLI: writes the copied seed, then Gradle verifies.
 *
 * <p>No Cloud Agents and no GitHub {@code repoUrl}.
 */
public final class CliCursorImplementer implements ImplementationPort {

    private static final Logger log = LoggerFactory.getLogger(CliCursorImplementer.class);

    private final String cliCommand;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final Path seedDir;
    private final Path workspaceDir;
    private final Path gradleWrapper;
    private final CursorCliExecutor executor;

    public CliCursorImplementer(
            String cliCommand,
            String apiKey,
            String model,
            Duration timeout,
            Path seedDir,
            Path workspaceDir,
            Path gradleWrapper,
            CursorCliExecutor executor) {
        this.cliCommand = cliCommand == null || cliCommand.isBlank() ? "agent" : cliCommand;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "composer-2.5" : model;
        this.timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
        this.seedDir = seedDir;
        this.workspaceDir = workspaceDir;
        this.gradleWrapper = gradleWrapper;
        this.executor = executor;
    }

    @Override
    public ImplementationResult implement(ImplementationRequest request) {
        if (apiKey.isBlank()) {
            return ImplementationResult.cli(
                    "CURSOR_MODE=cli requires CURSOR_API_KEY. Use CURSOR_MODE=stub for the local patch demo.",
                    false,
                    "CURSOR_API_KEY is not set");
        }
        String threadId = request.threadId() == null || request.threadId().isBlank()
                ? "local"
                : request.threadId();
        Path work = workspaceDir.resolve(threadId).resolve("ping-service-java");
        try {
            SeedWorkspace.copySeed(seedDir, work);
            String stdout = executor.execute(argv(work, request.prompt()), work, timeout);
            SeedWorkspace.ProcessResult gradle = SeedWorkspace.runGradleTest(gradleWrapper, work);
            boolean passed = gradle.exitCode() == 0;
            log.info("CLI implementer finished thread={} passed={}", threadId, passed);
            return ImplementationResult.cli(
                    passed
                            ? "Cursor CLI edited the workspace and Gradle test passed"
                            : "Cursor CLI finished but Gradle test failed",
                    passed,
                    stdout + "\n" + gradle.output());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ImplementationResult.cli("CLI implementer failed: " + ex.getMessage(), false, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ImplementationResult.cli(ex.getMessage(), false, ex.getMessage());
        }
    }

    List<String> argv(Path workspace, String prompt) {
        List<String> command = new ArrayList<>();
        command.add(cliCommand);
        command.add("-p");
        command.add("--output-format");
        command.add("text");
        command.add("--trust");
        command.add("--workspace");
        command.add(workspace.toString());
        command.add("--force");
        command.add("--model");
        command.add(model);
        if (!apiKey.isBlank()) {
            command.add("--api-key");
            command.add(apiKey);
        }
        command.add(prompt == null ? "" : prompt);
        return List.copyOf(command);
    }
}
