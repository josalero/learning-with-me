package dev.mytechprofile.sdlc.config;

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
 * Spawns the Cursor CLI with a timeout and kills the process tree on stall.
 *
 * <p><strong>When to use:</strong> live {@link ChatModelFactory} wiring. Tests should fake {@link
 * CursorCliExecutor} instead.
 *
 * <p><strong>Example:</strong> {@code new DefaultCursorCliExecutor().execute(argv, home, timeout)}.
 */
public final class DefaultCursorCliExecutor implements CursorCliExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultCursorCliExecutor.class);
    static final int MAX_OUTPUT_CHARS = 80_000;
    private static final Duration DRAIN_GRACE = Duration.ofSeconds(5);

    @Override
    public String execute(List<String> argv, Path workingDirectory, Duration timeout) {
        if (argv == null || argv.isEmpty()) {
            throw new IllegalStateException("Cursor CLI argv must be a non-empty array");
        }
        Duration wait = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofMinutes(3) : timeout;
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
                    // Pipe closed with the process; keep whatever was captured.
                }
            });
            boolean finished = process.waitFor(wait.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("Cursor CLI timed out after {} in {}", wait, cwd);
                kill(process);
                drain.join(DRAIN_GRACE);
                throw new IllegalStateException("Cursor CLI timed out after " + wait
                        + ". Check `agent` is on PATH and raise SDLC_LLM_TIMEOUT.");
            }
            drain.join(DRAIN_GRACE);
            String output = truncate(buffer.toString(StandardCharsets.UTF_8));
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IllegalStateException("Cursor CLI exited " + exit
                        + ". Install `agent`, run `agent login` or set CURSOR_API_KEY, and retry. Output: " + output);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cursor CLI was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to start Cursor CLI (`" + argv.getFirst()
                            + "`). Install it, put it on PATH, or set SDLC_CURSOR_CLI to the binary. Cause: "
                            + ex.getMessage(),
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
