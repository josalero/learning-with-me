package dev.mytechprofile.langgraph4j.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

class CliCursorImplementerTest {

    @TempDir
    Path workspace;

    @Test
    void argv_usesWorkspaceAndForce_notAskModeOrRepoUrl() {
        Path work = workspace.resolve("ping");
        CliCursorImplementer implementer = implementer((argv, cwd, timeout) -> "");

        List<String> argv = implementer.argv(work, "Return ok from PingService");

        assertEquals("agent", argv.getFirst());
        assertTrue(argv.contains("-p"));
        assertTrue(argv.contains("--force"));
        assertTrue(argv.contains(work.toString()));
        assertFalse(argv.contains("--mode"));
        assertFalse(argv.contains("ask"));
        assertFalse(argv.contains("--repo"));
        assertEquals("Return ok from PingService", argv.getLast());
    }

    @Test
    void implement_whenCliEditsPingService_gradlePasses() {
        Path root = Path.of("").toAbsolutePath();
        AtomicReference<Path> cwd = new AtomicReference<>();
        CliCursorImplementer implementer = new CliCursorImplementer(
                "agent",
                "test-key",
                "composer-2.5",
                Duration.ofMinutes(10),
                root.resolve("seeds/ping-service-java"),
                workspace,
                root.resolve("gradlew"),
                (argv, workingDirectory, timeout) -> {
                    cwd.set(workingDirectory);
                    try {
                        Files.writeString(
                                workingDirectory.resolve("src/main/java/ping/PingService.java"),
                                """
                                package ping;

                                public class PingService {
                                    public String status() {
                                        return "ok";
                                    }
                                }
                                """,
                                StandardCharsets.UTF_8);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    return "patched PingService";
                });

        ImplementationResult result = implementer.implement(
                new ImplementationRequest("cli-test", "Return ok from PingService", "", "main"));

        assertTrue(result.passed(), () -> result.verifyOutput());
        assertEquals("CLI", result.mode());
        assertTrue(result.summary().contains("Gradle test passed"));
        assertEquals(
                workspace.resolve("cli-test").resolve("ping-service-java"),
                cwd.get());
    }

    @Test
    void implement_whenApiKeyMissing_doesNotCallCli() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        CliCursorImplementer implementer = new CliCursorImplementer(
                "agent",
                "",
                "composer-2.5",
                Duration.ofMinutes(10),
                Path.of("seeds"),
                workspace,
                Path.of("gradlew"),
                (argv, cwd, timeout) -> {
                    called.set(true);
                    return "";
                });

        ImplementationResult result = implementer.implement(
                new ImplementationRequest("cli-test", "brief", "", "main"));

        assertFalse(result.passed());
        assertFalse(called.get());
        assertTrue(result.summary().contains("CURSOR_API_KEY"));
    }

    private CliCursorImplementer implementer(CursorCliExecutor executor) {
        return new CliCursorImplementer(
                "agent",
                "test-key",
                "composer-2.5",
                Duration.ofMinutes(10),
                Path.of("seeds"),
                workspace,
                Path.of("gradlew"),
                executor);
    }
}
