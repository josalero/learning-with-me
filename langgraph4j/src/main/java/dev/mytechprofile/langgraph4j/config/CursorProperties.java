package dev.mytechprofile.langgraph4j.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Implementer settings. {@link Mode#CLI} uses the local {@code agent} binary. Cloud fields
 * apply only when {@link Mode#CLOUD}. The API key is never logged.
 */
@Validated
@ConfigurationProperties(prefix = "cursor")
public record CursorProperties(
        @NotNull Mode mode,
        String apiKey,
        String apiBaseUrl,
        String defaultRepoUrl,
        String model,
        Duration pollInterval,
        Duration pollTimeout,
        boolean autoCreatePr,
        @NotBlank String seedDir,
        @NotBlank String workspaceDir,
        String cliCommand,
        Duration implementTimeout) {

    public enum Mode {
        STUB,
        CLI,
        CLOUD
    }

    public CursorProperties {
        apiKey = apiKey == null ? "" : apiKey;
        apiBaseUrl = blankTo(apiBaseUrl, "https://api.cursor.com");
        defaultRepoUrl = defaultRepoUrl == null ? "" : defaultRepoUrl;
        model = blankTo(model, "composer-2.5");
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
                ? Duration.ofSeconds(5)
                : pollInterval;
        pollTimeout = pollTimeout == null || pollTimeout.isZero() || pollTimeout.isNegative()
                ? Duration.ofMinutes(15)
                : pollTimeout;
        cliCommand = blankTo(cliCommand, "agent");
        implementTimeout = implementTimeout == null || implementTimeout.isZero() || implementTimeout.isNegative()
                ? Duration.ofMinutes(10)
                : implementTimeout;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
