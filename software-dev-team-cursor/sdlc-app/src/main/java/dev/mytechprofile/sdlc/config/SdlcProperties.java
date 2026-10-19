package dev.mytechprofile.sdlc.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem layout for catalogs, workspaces, and run artifacts.
 *
 * <p>Sample: {@code sdlc.home=.} resolves teams from {@code ./config/teams}.
 * {@code sdlc.llm-timeout=PT3M} is the Cursor CLI timeout per chat call.
 * Live L2 uses {@link DeveloperImplementer#CURSOR} unless {@code sdlc.offline=true}.
 */
@ConfigurationProperties(prefix = "sdlc")
public record SdlcProperties(
        Path home,
        String cursorApiKey,
        String cursorCliCommand,
        int maxTokens,
        Duration llmTimeout,
        String modelFast,
        String modelStrong,
        int fileReadLimitChars,
        Duration humanApprovalTimeout,
        boolean offline,
        DeveloperImplementer developerImplementer) {

    /**
     * Who writes files during L2 and review/QA rework.
     *
     * <p>Sample: {@code CURSOR} runs the local {@code agent} CLI on {@code workspace/};
     * {@code TOOLS} keeps LangChain4j {@code DeveloperTools}.
     */
    public enum DeveloperImplementer {
        TOOLS,
        CURSOR
    }

    /**
     * Builds properties with the live default implementer ({@link DeveloperImplementer#CURSOR}).
     *
     * <p>Sample: {@code SdlcProperties.of(home, "", "agent", 256, null, "", "", 8_000,
     * Duration.ofMinutes(1), true)} for offline tests.
     *
     * @param home catalog root
     * @param cursorApiKey Cursor API key, or blank
     * @param cursorCliCommand CLI binary
     * @param maxTokens unused token cap kept for YAML compatibility
     * @param llmTimeout per ask-mode CLI call
     * @param modelFast cheap model id
     * @param modelStrong coding model id
     * @param fileReadLimitChars file-tool cap
     * @param humanApprovalTimeout HITL wait
     * @param offline canned models and {@code DeveloperTools}
     * @return properties with {@code developerImplementer} defaulted
     */
    public static SdlcProperties of(
            Path home,
            String cursorApiKey,
            String cursorCliCommand,
            int maxTokens,
            Duration llmTimeout,
            String modelFast,
            String modelStrong,
            int fileReadLimitChars,
            Duration humanApprovalTimeout,
            boolean offline) {
        return new SdlcProperties(
                home,
                cursorApiKey,
                cursorCliCommand,
                maxTokens,
                llmTimeout,
                modelFast,
                modelStrong,
                fileReadLimitChars,
                humanApprovalTimeout,
                offline,
                null);
    }

    public SdlcProperties {
        home = (home == null ? Path.of(".") : home).toAbsolutePath().normalize();
        cursorApiKey = cursorApiKey == null ? "" : cursorApiKey;
        cursorCliCommand = blankTo(cursorCliCommand, "agent");
        if (maxTokens <= 0) {
            maxTokens = 8192;
        }
        llmTimeout = llmTimeout == null || llmTimeout.isZero() || llmTimeout.isNegative()
                ? Duration.ofMinutes(3)
                : llmTimeout;
        modelFast = blankTo(modelFast, "composer-2.5");
        modelStrong = blankTo(modelStrong, "composer-2.5");
        if (fileReadLimitChars <= 0) {
            fileReadLimitChars = 80_000;
        }
        humanApprovalTimeout = humanApprovalTimeout == null ? Duration.ofMinutes(30) : humanApprovalTimeout;
        developerImplementer = developerImplementer == null ? DeveloperImplementer.CURSOR : developerImplementer;
    }

    /**
     * Returns true when L2 should shell out to the local Cursor agent on the seeded repo.
     *
     * <p>Offline runs always stay on {@code DeveloperTools} so {@code ./gradlew check} never
     * starts {@code agent}.
     *
     * @return whether the live Cursor implementer is active
     */
    public boolean useLocalCursorDeveloper() {
        return !offline && developerImplementer == DeveloperImplementer.CURSOR;
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
