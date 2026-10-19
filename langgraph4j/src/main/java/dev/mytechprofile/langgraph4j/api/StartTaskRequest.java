package dev.mytechprofile.langgraph4j.api;

import jakarta.validation.constraints.Size;

/**
 * Starts a new implementation thread.
 *
 * <pre>{@code
 * { "brief": "Return ok from PingService", "repoUrl": "" }
 * }</pre>
 */
public record StartTaskRequest(
        @Size(max = 4000) String brief,
        @Size(max = 500) String repoUrl,
        @Size(max = 200) String startingRef) {
}
