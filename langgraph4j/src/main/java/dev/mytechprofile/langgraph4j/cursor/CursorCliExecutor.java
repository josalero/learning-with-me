package dev.mytechprofile.langgraph4j.cursor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Runs one Cursor {@code agent} process and returns stdout.
 *
 * <p>Tests fake this so {@code ./gradlew check} never starts the real CLI.
 */
@FunctionalInterface
public interface CursorCliExecutor {

    /**
     * Starts {@code argv} in {@code workingDirectory}.
     *
     * <pre>{@code
     * executor.execute(List.of("agent", "-p", prompt), workspace, Duration.ofMinutes(10));
     * }</pre>
     *
     * @param argv command; last argument is the prompt
     * @param workingDirectory process cwd and {@code --workspace}
     * @param timeout kill the process after this duration
     * @return combined stdout/stderr
     */
    String execute(List<String> argv, Path workingDirectory, Duration timeout);
}
