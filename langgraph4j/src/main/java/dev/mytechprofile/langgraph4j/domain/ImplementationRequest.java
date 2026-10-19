package dev.mytechprofile.langgraph4j.domain;

/**
 * Work the implementer node sends to the stub or local Cursor CLI.
 *
 * <pre>{@code
 * new ImplementationRequest(threadId, prompt, "", "main");
 * }</pre>
 *
 * {@code repoUrl} is unused for stub and CLI.
 */
public record ImplementationRequest(
        String threadId,
        String prompt,
        String repoUrl,
        String startingRef) {
}
