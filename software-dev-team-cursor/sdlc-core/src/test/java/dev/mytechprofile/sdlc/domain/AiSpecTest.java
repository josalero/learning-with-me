package dev.mytechprofile.sdlc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiSpecTest {

    @Test
    void covers_whenEveryAcceptanceCriterionHasAPlannedTest_returnsTrue() {
        FeatureBrief brief = new FeatureBrief(
                "404",
                "unknown user",
                List.of(),
                List.of(
                        new AcceptanceCriterion("AC-1", "unknown id", "GET", "404"),
                        new AcceptanceCriterion("AC-2", "blank name", "POST", "400")),
                List.of(),
                "must");
        AiSpec spec = new AiSpec(
                "add validation",
                List.of("UserController.java"),
                "RFC 9457",
                "User",
                List.of("unknownIdReturns404"),
                List.of(),
                List.of(
                        new TraceabilityItem("AC-1", "unknownIdReturns404"),
                        new TraceabilityItem("AC-2", "blankNameRejected")));

        assertThat(spec.covers(brief)).isTrue();
    }

    @Test
    void covers_whenACriterionIsMissingFromTraceability_returnsFalse() {
        FeatureBrief brief = new FeatureBrief(
                "404",
                "unknown user",
                List.of(),
                List.of(new AcceptanceCriterion("AC-1", "g", "w", "t")),
                List.of(),
                "must");
        AiSpec spec = new AiSpec(
                "partial", List.of(), "", "", List.of(), List.of(), List.of(new TraceabilityItem("AC-99", "someTest")));

        assertThat(spec.covers(brief)).isFalse();
    }
}
