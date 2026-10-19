package dev.mytechprofile.langgraph4j.api;

import dev.mytechprofile.langgraph4j.domain.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Human decision collected at the graph interrupt.
 *
 * <pre>{@code
 * { "decision": "APPROVE", "promptOverride": null }
 * }</pre>
 */
public record ResumeTaskRequest(
        @NotNull Decision decision,
        @Size(max = 8000) String promptOverride) {
}
