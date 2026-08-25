package dev.mytechprofile.sdlc.catalog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Technology profile for a target repository.
 *
 * <p>Sample: {@code users-service-java} with Gradle argv commands and {@code src/main/**} globs.
 */
public record ProjectProfile(
        String id,
        Path seed,
        Path repoPath,
        String branchPrefix,
        List<String> sourceGlobs,
        Path conventions,
        Map<String, List<String>> commands,
        Duration timeout,
        Path javaHome) {

    public ProjectProfile {
        id = Objects.requireNonNull(id, "project id is required").trim();
        seed = Objects.requireNonNull(seed, "seed is required");
        repoPath = Objects.requireNonNull(repoPath, "repoPath is required");
        branchPrefix = Objects.requireNonNullElse(branchPrefix, "feature/");
        sourceGlobs = sourceGlobs == null || sourceGlobs.isEmpty() ? List.of("**/*") : List.copyOf(sourceGlobs);
        commands = commands == null ? Map.of() : Map.copyOf(commands);
        timeout = timeout == null ? Duration.ofSeconds(600) : timeout;
        if (id.isBlank()) {
            throw new IllegalArgumentException("project id must be non-blank");
        }
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("project " + id + " must declare at least one command");
        }
        commands.forEach((name, argv) -> {
            if (argv == null || argv.isEmpty()) {
                throw new IllegalArgumentException("command " + name + " must be a non-empty argv array");
            }
        });
    }

    public List<String> command(String name) {
        List<String> argv = commands.get(name);
        if (argv == null) {
            throw new IllegalArgumentException("project " + id + " has no allowlisted command named '" + name + "'");
        }
        return argv;
    }

    public boolean hasCommand(String name) {
        return commands.containsKey(name);
    }
}
