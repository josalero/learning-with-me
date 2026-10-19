package dev.mytechprofile.langgraph4j.cursor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

/**
 * Local stand-in for Cursor: copies the ping seed, patches {@code PingService}, runs Gradle.
 */
public final class StubCursorImplementer implements ImplementationPort {

    private static final Logger log = LoggerFactory.getLogger(StubCursorImplementer.class);

    private static final String PING_SOURCE = """
            package ping;

            public class PingService {
                public String status() {
                    return "ok";
                }
            }
            """;

    private final Path seedDir;
    private final Path workspaceDir;
    private final Path gradleWrapper;

    public StubCursorImplementer(Path seedDir, Path workspaceDir, Path gradleWrapper) {
        this.seedDir = seedDir;
        this.workspaceDir = workspaceDir;
        this.gradleWrapper = gradleWrapper;
    }

    @Override
    public ImplementationResult implement(ImplementationRequest request) {
        String threadId = request.threadId() == null || request.threadId().isBlank()
                ? "local"
                : request.threadId();
        Path work = workspaceDir.resolve(threadId).resolve("ping-service-java");
        try {
            SeedWorkspace.copySeed(seedDir, work);
            Path ping = work.resolve("src/main/java/ping/PingService.java");
            Files.createDirectories(ping.getParent());
            Files.writeString(ping, PING_SOURCE, StandardCharsets.UTF_8);
            SeedWorkspace.ProcessResult gradle = SeedWorkspace.runGradleTest(gradleWrapper, work);
            boolean passed = gradle.exitCode() == 0;
            String summary = passed
                    ? "Stub patched PingService.status() to ok and Gradle test passed"
                    : "Stub patched PingService but Gradle test failed";
            log.info("Stub implementer finished thread={} passed={}", threadId, passed);
            return ImplementationResult.stub(summary, passed, gradle.output());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ImplementationResult.stub(
                    "Stub implementer failed: " + ex.getMessage(),
                    false,
                    ex.getMessage());
        }
    }
}
