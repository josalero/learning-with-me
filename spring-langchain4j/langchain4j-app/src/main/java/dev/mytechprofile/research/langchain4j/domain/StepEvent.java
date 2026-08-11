package dev.mytechprofile.research.langchain4j.domain;

/**
 * One agent step emitted on the SSE stream and included in {@link ResearchReport#steps()}.
 *
 * @param input  human-readable agent inputs for the UI timeline
 * @param output human-readable agent outputs for the UI timeline
 */
public record StepEvent(
        String agent,
        String status,
        String input,
        String output,
        long elapsedMs
) {
}
