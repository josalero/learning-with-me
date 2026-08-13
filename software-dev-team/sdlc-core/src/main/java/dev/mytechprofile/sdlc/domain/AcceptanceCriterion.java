package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * One Given/When/Then acceptance criterion.
 *
 * <p>Sample: {@code new AcceptanceCriterion("AC-1", "a user id is unknown", "GET /users/{id}",
 * "the API returns 404 with a problem detail")}.
 */
public record AcceptanceCriterion(String id, String given, String when, String then) {

    public AcceptanceCriterion {
        id = Objects.requireNonNullElse(id, "").trim();
        given = Objects.requireNonNullElse(given, "").trim();
        when = Objects.requireNonNullElse(when, "").trim();
        then = Objects.requireNonNullElse(then, "").trim();
    }

    public boolean isComplete() {
        return !id.isBlank() && !given.isBlank() && !when.isBlank() && !then.isBlank();
    }
}
