package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * One agent or gate invocation recorded for the run timeline.
 *
 * <p>Sample: {@code new StepEvent("developer", "completed", "implement feature", "3 files", 1200)}.
 */
public record StepEvent(String agent, String status, String inputSummary, String outputSummary, long elapsedMs) {

    public StepEvent {
        agent = Objects.requireNonNullElse(agent, "unknown");
        status = Objects.requireNonNullElse(status, "completed");
        inputSummary = Objects.requireNonNullElse(inputSummary, "");
        outputSummary = Objects.requireNonNullElse(outputSummary, "");
    }
}
