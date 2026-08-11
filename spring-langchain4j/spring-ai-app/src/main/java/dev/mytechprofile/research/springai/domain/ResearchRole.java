package dev.mytechprofile.research.springai.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical agent roles shared by step tracing and the UI timeline.
 */
public enum ResearchRole {
    PLANNER,
    RESEARCHER,
    WRITER,
    CRITIC;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ResearchRole> fromAgentName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("planner") || lower.equals("plan")) {
            return Optional.of(PLANNER);
        }
        if (lower.contains("researcher") || lower.equals("research")) {
            return Optional.of(RESEARCHER);
        }
        if (lower.contains("writer") || lower.equals("write")) {
            return Optional.of(WRITER);
        }
        if (lower.contains("critic") || lower.equals("critique")) {
            return Optional.of(CRITIC);
        }
        return Optional.empty();
    }
}
