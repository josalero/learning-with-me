package dev.mytechprofile.sdlc.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads prompt markdown from the SDLC home directory.
 *
 * <p><strong>When to use:</strong> building a role agent that needs its system message.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * String system = prompts.load("prompts/developer.md");
 * }</pre>
 */
public final class PromptLoader {

    private final Path home;

    /**
     * Creates a loader rooted at {@code home}.
     *
     * @param home SDLC home directory
     */
    public PromptLoader(Path home) {
        this.home = home;
    }

    /**
     * Reads a prompt file relative to home.
     *
     * @param relativePath path from the role YAML {@code prompt} field
     * @return trimmed prompt text
     */
    public String load(String relativePath) {
        Path file = home.resolve(relativePath).normalize();
        if (!file.startsWith(home.toAbsolutePath().normalize())
                && !file.toAbsolutePath().startsWith(home.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Prompt path escapes SDLC home: " + relativePath);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read prompt " + file + ". Check the role YAML.", ex);
        }
    }
}
