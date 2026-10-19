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
                SdlcProperties.of(home, "", "", 256, null, "", "", 8_000, Duration.ofMinutes(1), true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(properties.cursorCliCommand()).isEqualTo("agent");
        assertThat(properties.modelFast()).isEqualTo("composer-2.5");
        assertThat(properties.modelStrong()).isEqualTo("composer-2.5");
    }

    @Test
    void compactConstructor_whenLlmTimeoutIsZero_defaultsToThreeMinutes() {
        SdlcProperties properties = SdlcProperties.of(
                home, "", "agent", 256, Duration.ZERO, "fast", "strong", 8_000, Duration.ofMinutes(1), true);

        assertThat(properties.llmTimeout()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void useLocalCursorDeveloper_whenOffline_isFalse() {
        SdlcProperties properties = new SdlcProperties(
                home,
                "key",
                "agent",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                true,
                SdlcProperties.DeveloperImplementer.CURSOR);

        assertThat(properties.developerImplementer()).isEqualTo(SdlcProperties.DeveloperImplementer.CURSOR);
        assertThat(properties.useLocalCursorDeveloper()).isFalse();
    }

    @Test
    void useLocalCursorDeveloper_whenLiveAndCursor_isTrue() {
        SdlcProperties properties = SdlcProperties.of(
                home,
                "key",
                "agent",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                false);

        assertThat(properties.developerImplementer()).isEqualTo(SdlcProperties.DeveloperImplementer.CURSOR);
        assertThat(properties.useLocalCursorDeveloper()).isTrue();
    }

    @Test
    void useLocalCursorDeveloper_whenLiveAndTools_isFalse() {
        SdlcProperties properties = new SdlcProperties(
                home,
                "key",
                "agent",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                false,
                SdlcProperties.DeveloperImplementer.TOOLS);

        assertThat(properties.useLocalCursorDeveloper()).isFalse();
    }
}
