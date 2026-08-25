package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * QA result for one acceptance criterion.
 *
 * <p>Sample: {@code new QaResult("AC-1", "PASS", "testUnknownUserReturns404 passed")}.
 */
public record QaResult(String acceptanceCriterionId, String status, String evidence) {

    public QaResult {
        acceptanceCriterionId =
                Objects.requireNonNullElse(acceptanceCriterionId, "").trim();
        status = Objects.requireNonNullElse(status, "FAIL").trim().toUpperCase();
        evidence = Objects.requireNonNullElse(evidence, "").trim();
    }

    public boolean passed() {
        return "PASS".equals(status);
    }
}
