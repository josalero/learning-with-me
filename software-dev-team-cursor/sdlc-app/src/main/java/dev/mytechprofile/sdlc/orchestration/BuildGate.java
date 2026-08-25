package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.sdlc.adapter.JunitXmlReports;
import dev.mytechprofile.sdlc.domain.ArtifactFile;
import dev.mytechprofile.sdlc.domain.BuildResult;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.CommandRunner;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import java.nio.file.Path;

/**
 * Runs the allowlisted {@code test} command, or reuses the last result when the tree is clean.
 *
 * <p><strong>When to use:</strong> after a Developer turn, and after review/QA rework. Skip when
 * the agent wrote no files — re-running Gradle cannot change the outcome.
 *
 * <p><strong>Example:</strong> first call runs {@code ./gradlew test}; a later call with no git
 * changes returns the same {@link BuildResult} and emits {@code skipped}.
 */
public final class BuildGate {

    private final CommandRunner commandRunner;
    private final VersionControlPort git;
    private final ArtifactStore artifacts;

    /**
     * Creates a gate bound to the run's command runner and git.
     *
     * @param commandRunner allowlisted test argv
     * @param git working-tree dirty check
     * @param artifacts {@code 04-build-result.json}
     */
    public BuildGate(CommandRunner commandRunner, VersionControlPort git, ArtifactStore artifacts) {
        this.commandRunner = commandRunner;
        this.git = git;
        this.artifacts = artifacts;
    }

    /**
     * Returns whether a previous result can be reused.
     *
     * @param dirty {@code true} when git reports uncommitted changes
     * @param previous last gate result, possibly {@link BuildResult#none()}
     * @return {@code true} to skip the process
     */
    public static boolean shouldSkip(boolean dirty, BuildResult previous) {
        return !dirty && previous.evaluated();
    }

    /**
     * Runs tests or reuses {@code previous} when the working tree is unchanged.
     *
     * @param context run handles
     * @param scope agentic state
     * @return current build result
     */
    public BuildResult evaluate(RunContext context, AgenticScope scope) {
        BuildResult previous = RunStateFactory.read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
        boolean dirty = git.hasChanges(context.workspace().root());
        if (shouldSkip(dirty, previous)) {
            BuildResult reused =
                    attachExecutedTests(previous, context.workspace().root());
            writeBuildState(scope, context, reused);
            context.events()
                    .accept(new StepEvent("build-gate", "skipped", "test", "no file changes since last test", 0L));
            return reused;
        }
        long started = System.currentTimeMillis();
        BuildResult result = BuildResult.from(
                commandRunner.run(context.project(), context.workspace().root(), "test"));
        if (result.success()) {
            result = new BuildResult(
                    true,
                    result.exitCode(),
                    result.truncatedOutput()
                            + System.lineSeparator()
                            + "Tests passed (exit 0). A Gradle UP-TO-DATE test task still means the tests passed.",
                    result.durationMs(),
                    result.timedOut());
            result = attachExecutedTests(result, context.workspace().root());
        }
        writeBuildState(scope, context, result);
        context.events()
                .accept(new StepEvent(
                        "build-gate",
                        result.success() ? "passed" : "failed",
                        "test",
                        "exitCode=" + result.exitCode(),
                        System.currentTimeMillis() - started));
        return result;
    }

    /**
     * Adds JUnit method names to a green result so QA does not treat a truncated log as missing tests.
     *
     * @param result current gate result
     * @param workspaceRoot directory the test command ran in
     * @return {@code result} with {@link BuildResult#executedTests()} filled when reports exist
     */
    static BuildResult attachExecutedTests(BuildResult result, Path workspaceRoot) {
        if (!result.success()) {
            return result;
        }
        return result.withExecutedTests(JunitXmlReports.readExecutedTests(workspaceRoot));
    }

    private void writeBuildState(AgenticScope scope, RunContext context, BuildResult result) {
        scope.writeState(StateKeys.BUILD_RESULT, result);
        scope.writeState(StateKeys.BUILD_FEEDBACK, result.truncatedOutput());
        scope.writeState(StateKeys.BUILD_OUTPUT, result.truncatedOutput());
        artifacts.writeJson(context.runId(), ArtifactFile.BUILD_RESULT.fileName(), result);
    }
}
