package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * Maps one acceptance criterion to a planned test.
 *
 * <p>Sample: {@code new TraceabilityItem("AC-1", "UserControllerTest.unknownIdReturns404")}.
 */
public record TraceabilityItem(String acceptanceCriterionId, String plannedTest) {

    public TraceabilityItem {
        acceptanceCriterionId =
                Objects.requireNonNullElse(acceptanceCriterionId, "").trim();
        plannedTest = Objects.requireNonNullElse(plannedTest, "").trim();
    }

    public boolean isComplete() {
        return !acceptanceCriterionId.isBlank() && !plannedTest.isBlank();
    }
}
