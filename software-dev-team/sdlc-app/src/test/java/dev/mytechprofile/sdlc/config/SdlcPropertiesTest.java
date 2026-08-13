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
        SdlcProperties properties = new SdlcProperties(
                home,
                "",
                "https://openrouter.ai/api/v1",
                256,
                null,
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void compactConstructor_whenLlmTimeoutIsZero_defaultsToThreeMinutes() {
        SdlcProperties properties = new SdlcProperties(
                home,
                "",
                "https://openrouter.ai/api/v1",
                256,
                Duration.ZERO,
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
    }
}
