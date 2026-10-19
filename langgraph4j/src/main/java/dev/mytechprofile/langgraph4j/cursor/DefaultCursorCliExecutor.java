package dev.mytechprofile.langgraph4j.cursor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawns the Cursor CLI with a timeout. Does not log the API key.
 */
public final class DefaultCursorCliExecutor implements CursorCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultCursorCliExecutor.class);
    private static final int MAX_OUTPUT_CHARS = 80_000;
    private static final Duration DRAIN_GRACE = Duration.ofSeconds(5);

    @Override
    public String execute(List<String> argv, Path workingDirectory, Duration timeout) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalStateException("Cursor CLI argv must be a non-empty array");
        }
        Duration wait = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofMinutes(10)
                : timeout;
        Path cwd = workingDirectory == null ? Path.of(".") : workingDirectory;
        log.info("Running Cursor CLI binary={} cwd={} timeout={}", argv.getFirst(), cwd, wait);
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread drain = Thread.ofVirtual().start(() -> {
                try (InputStream stdout = process.getInputStream()) {
                    stdout.transferTo(buffer);
                } catch (IOException ignored) {
                    // Pipe closed with the process
                }
            });
            boolean finished = process.waitFor(wait.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("Cursor CLI timed out after {} in {}", wait, cwd);
                kill(process);
                drain.join(DRAIN_GRACE);
                throw new IllegalStateException(
                        "Cursor CLI timed out after " + wait + ". Check `agent` is on PATH and raise CURSOR_IMPLEMENT_TIMEOUT.");
            }
            drain.join(DRAIN_GRACE);
            String output = truncate(buffer.toString(StandardCharsets.UTF_8));
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IllegalStateException(
                        "Cursor CLI exited " + exit
                                + ". Install `agent` and set CURSOR_API_KEY. Output: " + output);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cursor CLI was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to start Cursor CLI (`" + argv.getFirst()
                            + "`). Install it or set CURSOR_CLI. Cause: " + ex.getMessage(),
                    ex);
        }
    }

    private static void kill(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(DRAIN_GRACE.toSeconds(), TimeUnit.SECONDS);
    }

    static String truncate(String text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS - 3) + "...";
    }
}
