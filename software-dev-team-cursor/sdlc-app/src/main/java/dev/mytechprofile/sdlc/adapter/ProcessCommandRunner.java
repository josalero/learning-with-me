package dev.mytechprofile.sdlc.adapter;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.domain.CommandResult;
import dev.mytechprofile.sdlc.port.CommandNotAllowedException;
import dev.mytechprofile.sdlc.port.CommandRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes allowlisted argv arrays. Never interpolates a shell string.
 *
 * <p>Gradle invocations get an isolated {@code GRADLE_USER_HOME}, {@code --no-daemon}, and
 * cleared {@code JAVA_OPTS}/{@code GRADLE_OPTS} so a nested build cannot collide with the
 * orchestrator daemon.
 *
 * <p>Sample: {@code runner.run(profile, repo, "test")}.
 */
public final class ProcessCommandRunner implements CommandRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessCommandRunner.class);
    static final int MAX_OUTPUT_CHARS = 12_000;
    private static final Duration DRAIN_GRACE = Duration.ofSeconds(5);

    private final Path gradleUserHome;

    public ProcessCommandRunner(Path gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    @Override
    public CommandResult run(ProjectProfile profile, Path workingDirectory, String commandName) {
        if (!profile.hasCommand(commandName)) {
            throw new CommandNotAllowedException(
                    "Command '" + commandName + "' is not allowlisted for project " + profile.id()
                            + ". Declared commands: " + profile.commands().keySet());
        }
        return run(profile, workingDirectory, profile.command(commandName));
    }

    @Override
    public CommandResult run(ProjectProfile profile, Path workingDirectory, List<String> argv) {
        if (argv == null || argv.isEmpty()) {
            throw new CommandNotAllowedException("Command argv must be a non-empty array");
        }
        if (!isAllowlisted(profile, argv)) {
            throw new CommandNotAllowedException(
                    "Command argv is not allowlisted for project " + profile.id() + ": " + argv);
        }
        List<String> command = prepareArgv(argv);
        long started = System.currentTimeMillis();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.remove("JAVA_OPTS");
        env.remove("GRADLE_OPTS");
        if (isGradle(command)) {
            try {
                Files.createDirectories(gradleUserHome);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create GRADLE_USER_HOME " + gradleUserHome, ex);
            }
            env.put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());
        }
        if (profile.javaHome() != null) {
            env.put("JAVA_HOME", profile.javaHome().toAbsolutePath().toString());
        }
        Duration timeout = profile.timeout();
        log.info("Running command={} cwd={}", command, workingDirectory);
        try {
            Process process = builder.start();
            // Drain on another thread: transferTo blocks until EOF, so reading inline would let a
            // stalled child (for example a Gradle wrapper download) outlive the timeout forever.
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread drain = Thread.ofVirtual().start(() -> {
                try (InputStream stdout = process.getInputStream()) {
                    stdout.transferTo(buffer);
                } catch (IOException ignored) {
                    // Pipe closed with the process; keep whatever was captured.
                }
            });
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("Command {} timed out after {} in {}", command, timeout, workingDirectory);
                kill(process);
                drain.join(DRAIN_GRACE);
                String output = truncate(buffer.toString(StandardCharsets.UTF_8)
                        + System.lineSeparator()
                        + "Timed out after " + timeout + " and was killed.");
                return new CommandResult(-1, output, System.currentTimeMillis() - started, true);
            }
            drain.join(DRAIN_GRACE);
            String output = truncate(buffer.toString(StandardCharsets.UTF_8));
            return new CommandResult(process.exitValue(), output, System.currentTimeMillis() - started, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "interrupted", System.currentTimeMillis() - started, true);
        } catch (IOException ex) {
            return new CommandResult(
                    -1, "Failed to start command: " + ex.getMessage(), System.currentTimeMillis() - started, false);
        }
    }

    /**
     * Kills the child and any grandchildren, so a wrapper script never leaves a build running.
     */
    private static void kill(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(DRAIN_GRACE.toSeconds(), TimeUnit.SECONDS);
    }

    private static boolean isAllowlisted(ProjectProfile profile, List<String> argv) {
        return profile.commands().values().stream().anyMatch(allowed -> allowed.equals(argv));
    }

    static List<String> prepareArgv(List<String> argv) {
        List<String> copy = new ArrayList<>(argv);
        if (isGradle(copy) && copy.stream().noneMatch(arg -> arg.equals("--no-daemon"))) {
            copy.add("--no-daemon");
        }
        return List.copyOf(copy);
    }

    private static boolean isGradle(List<String> argv) {
        String binary = argv.get(0).toLowerCase(Locale.ROOT);
        return binary.contains("gradle");
    }

    static String truncate(String text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS - 3) + "...";
    }
}
