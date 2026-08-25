package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdlcPropertiesTest {

    @TempDir
    Path home;

    @Test
    void compactConstructor_whenLlmTimeoutMissing_defaultsToThreeMinutes() {
        SdlcProperties properties =
                new SdlcProperties(home, "", "", 256, null, "", "", 8_000, Duration.ofMinutes(1), true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(properties.cursorCliCommand()).isEqualTo("agent");
        assertThat(properties.modelFast()).isEqualTo("composer-2.5");
        assertThat(properties.modelStrong()).isEqualTo("composer-2.5");
    }

    @Test
    void compactConstructor_whenLlmTimeoutIsZero_defaultsToThreeMinutes() {
        SdlcProperties properties = new SdlcProperties(
                home, "", "agent", 256, Duration.ZERO, "fast", "strong", 8_000, Duration.ofMinutes(1), true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
    }
}
