package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * One PR review finding.
 *
 * <p>Sample: {@code new ReviewFinding("error", "UserController.java", "Missing test for 404")}.
 */
public record ReviewFinding(String severity, String file, String rationale) {

    public ReviewFinding {
        severity = Objects.requireNonNullElse(severity, "info").trim();
        file = Objects.requireNonNullElse(file, "").trim();
        rationale = Objects.requireNonNullElse(rationale, "").trim();
    }

    public boolean blocking() {
        return "error".equalsIgnoreCase(severity) || "blocker".equalsIgnoreCase(severity);
    }
}
