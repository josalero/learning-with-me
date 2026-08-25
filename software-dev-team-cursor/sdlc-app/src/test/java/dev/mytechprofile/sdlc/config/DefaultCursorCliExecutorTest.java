package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultCursorCliExecutorTest {

    @TempDir
    Path home;

    @Test
    void execute_whenEchoSucceeds_returnsStdout() {
        String output =
                new DefaultCursorCliExecutor().execute(List.of("/bin/echo", "cli-ok"), home, Duration.ofSeconds(5));

        assertThat(output).contains("cli-ok");
    }

    @Test
    void execute_whenBinaryMissing_explainsHowToInstall() {
        assertThatThrownBy(() -> new DefaultCursorCliExecutor()
                        .execute(List.of("cursor-cli-does-not-exist-xyz"), home, Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to start Cursor CLI")
                .hasMessageContaining("SDLC_CURSOR_CLI");
    }

    @Test
    void truncate_whenLongerThanCap_addsEllipsis() {
        String huge = "a".repeat(DefaultCursorCliExecutor.MAX_OUTPUT_CHARS + 10);

        assertThat(DefaultCursorCliExecutor.truncate(huge))
                .hasSize(DefaultCursorCliExecutor.MAX_OUTPUT_CHARS)
                .endsWith("...");
    }
}
