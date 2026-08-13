package dev.mytechprofile.sdlc.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem layout for catalogs, workspaces, and run artifacts.
 *
 * <p>Sample: {@code sdlc.home=.} resolves teams from {@code ./config/teams}.
 * {@code sdlc.llm-timeout=PT3M} is the OpenRouter HTTP timeout per chat call.
 */
@ConfigurationProperties(prefix = "sdlc")
public record SdlcProperties(
        Path home,
        String openrouterApiKey,
        String openrouterBaseUrl,
        int maxTokens,
        Duration llmTimeout,
        String modelFast,
        String modelStrong,
        int fileReadLimitChars,
        Duration humanApprovalTimeout,
        boolean offline) {

    public SdlcProperties {
        home = (home == null ? Path.of(".") : home).toAbsolutePath().normalize();
        openrouterApiKey = openrouterApiKey == null ? "" : openrouterApiKey;
        openrouterBaseUrl = openrouterBaseUrl == null || openrouterBaseUrl.isBlank()
                ? "https://openrouter.ai/api/v1"
                : openrouterBaseUrl;
        if (maxTokens <= 0) {
            maxTokens = 8192;
        }
        llmTimeout = llmTimeout == null || llmTimeout.isZero() || llmTimeout.isNegative()
                ? Duration.ofMinutes(3)
                : llmTimeout;
        modelFast = blankTo(modelFast, "openai/gpt-4o-mini");
        modelStrong = blankTo(modelStrong, "openai/gpt-4o-mini");
        if (fileReadLimitChars <= 0) {
            fileReadLimitChars = 80_000;
        }
        humanApprovalTimeout = humanApprovalTimeout == null ? Duration.ofMinutes(30) : humanApprovalTimeout;
    }

    public Path configDir() {
        return home.resolve("config");
    }

    public Path teamsDir() {
        return configDir().resolve("teams");
    }

    public Path projectsDir() {
        return configDir().resolve("projects");
    }

    public Path promptsDir() {
        return home.resolve("prompts");
    }

    public Path workspaceDir() {
        return home.resolve("workspace");
    }

    public Path runsDir() {
        return home.resolve("runs");
    }

    public Path seedsDir() {
        return home.resolve("seeds");
    }

    public Path gradleUserHome() {
        return workspaceDir().resolve(".gradle-home");
    }

    /**
     * Returns {@code docs/conventions} under home.
     *
     * @return conventions directory
     */
    public Path conventionsDir() {
        return home.resolve("docs").resolve("conventions");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
