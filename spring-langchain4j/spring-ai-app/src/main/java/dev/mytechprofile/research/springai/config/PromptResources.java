package dev.mytechprofile.research.springai.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads shared prompt text from {@code classpath:prompts/}.
 */
public final class PromptResources {

    private PromptResources() {
    }

    public static String load(String fileName) {
        String path = "prompts/" + fileName;
        try (InputStream in = PromptResources.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Failed to read prompt resource: " + path, ex);
        }
    }
}
