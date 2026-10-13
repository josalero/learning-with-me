package dev.mytechprofile.langchain122.screening;

import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.Scale;

/**
 * The two screening questions for one application, answered in a single decision call.
 */
public record ScreenResult(
        @Decide("Should a recruiter review this application?") boolean advance,
        @Decide("How well does it match the must-haves?") Scale<FitLevel> fit) {}
