package dev.mytechprofile.sdlc.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Runs one Cursor {@code agent} process and returns stdout.
 *
 * <p><strong>When to use:</strong> {@link CursorCliChatModel} injects this so unit tests never
 * spawn the real CLI.
 *
 * <p><strong>Example:</strong> {@code executor.execute(List.of("agent", "-p", "hello"), home,
 * Duration.ofMinutes(3))}.
 */
@FunctionalInterface
public interface CursorCliExecutor {

    /**
     * Starts {@code argv} in {@code workingDirectory} and returns combined stdout/stderr.
     *
     * @param argv command and flags; the last argument is the prompt
     * @param workingDirectory process cwd
     * @param timeout kill the process tree after this duration
     * @return process output
     */
    String execute(List<String> argv, Path workingDirectory, Duration timeout);
}
