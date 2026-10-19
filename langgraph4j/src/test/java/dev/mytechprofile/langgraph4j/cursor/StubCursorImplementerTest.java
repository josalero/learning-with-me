package dev.mytechprofile.langgraph4j.cursor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

class StubCursorImplementerTest {

    @TempDir
    Path workspace;

    @Test
    void patchesSeedAndPassesGradleTest() {
        Path root = Path.of("").toAbsolutePath();
        StubCursorImplementer implementer = new StubCursorImplementer(
                root.resolve("seeds/ping-service-java"),
                workspace,
                root.resolve("gradlew"));

        ImplementationResult result = implementer.implement(
                new ImplementationRequest("stub-test", "Return ok from PingService", "", "main"));

        assertTrue(result.passed(), () -> result.verifyOutput());
        assertTrue(result.summary().contains("Gradle test passed"));
    }
}
