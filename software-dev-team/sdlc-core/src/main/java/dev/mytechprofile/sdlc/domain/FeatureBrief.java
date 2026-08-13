package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * Product Owner output: the feature the team will build.
 *
 * <p>Sample: a brief titled "Unknown user 404" with two acceptance criteria and empty out of
 * scope.
 */
public record FeatureBrief(
        String title,
        String problem,
        List<String> userStories,
        List<AcceptanceCriterion> acceptanceCriteria,
        List<String> outOfScope,
        String priority) {

    public FeatureBrief {
        title = Objects.requireNonNullElse(title, "").trim();
        problem = Objects.requireNonNullElse(problem, "").trim();
        userStories = userStories == null ? List.of() : List.copyOf(userStories);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        outOfScope = outOfScope == null ? List.of() : List.copyOf(outOfScope);
        priority = Objects.requireNonNullElse(priority, "should").trim();
    }

    public static FeatureBrief fromRequest(String featureRequest) {
        String text = Objects.requireNonNullElse(featureRequest, "").trim();
        return new FeatureBrief(
                "Requested feature",
                text,
                List.of(text),
                List.of(new AcceptanceCriterion("AC-1", "the service is running", "the feature is invoked", text)),
                List.of(),
                "should");
    }

    public boolean allCriteriaComplete() {
        return !acceptanceCriteria.isEmpty() && acceptanceCriteria.stream().allMatch(AcceptanceCriterion::isComplete);
    }
}
