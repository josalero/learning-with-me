package dev.mytechprofile.langgraph4j.cursor;

import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;

/**
 * Port for the graph implement node. Stub patches locally; CLI runs {@code agent} on the workspace.
 */
public interface ImplementationPort {

    /**
     * Applies the approved prompt to a workspace or Cloud Agent.
     *
     * <pre>{@code
     * ImplementationResult result = port.implement(
     *     new ImplementationRequest(threadId, prompt, repoUrl, "main"));
     * }</pre>
     *
     * @param request approved prompt and optional remote repo
     * @return Cursor ids (or stub placeholders) plus verify outcome
     */
    ImplementationResult implement(ImplementationRequest request);
}
