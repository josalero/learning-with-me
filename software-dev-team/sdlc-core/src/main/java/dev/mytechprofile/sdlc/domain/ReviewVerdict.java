package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * PR Reviewer output against a real git diff.
 *
 * <p>Sample: {@code ReviewVerdict.approve()} or {@code REQUEST_CHANGES} with blocking findings.
 */
public record ReviewVerdict(String decision, List<ReviewFinding> findings, int blockingCount) {

    public static final String APPROVE = "APPROVE";
    public static final String REQUEST_CHANGES = "REQUEST_CHANGES";

    public ReviewVerdict {
        decision = Objects.requireNonNullElse(decision, REQUEST_CHANGES).trim().toUpperCase();
        findings = findings == null ? List.of() : List.copyOf(findings);
        blockingCount = (int) findings.stream().filter(ReviewFinding::blocking).count();
        if (blockingCount > 0) {
            decision = REQUEST_CHANGES;
        } else {
            decision = APPROVE;
        }
    }

    public static ReviewVerdict none() {
        return new ReviewVerdict(APPROVE, List.of(), 0);
    }

    public static ReviewVerdict approve() {
        return new ReviewVerdict(APPROVE, List.of(), 0);
    }

    public boolean approved() {
        return APPROVE.equals(decision) && blockingCount == 0;
    }

    public boolean requestsChanges() {
        return !approved();
    }
}
