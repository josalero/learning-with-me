package dev.mytechprofile.sdlc.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class TestHomes {

    static SdlcProperties properties(Path home) {
        return new SdlcProperties(
                home,
                "",
                "https://openrouter.ai/api/v1",
                256,
                Duration.ofMinutes(3),
                "fast",
                "strong",
                8_000,
                Duration.ofMinutes(1),
                true);
    }

    static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private TestHomes() {}
}
