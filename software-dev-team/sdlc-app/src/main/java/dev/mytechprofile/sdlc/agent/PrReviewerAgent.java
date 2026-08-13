package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ReviewVerdict;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * PR Reviewer agent that judges a real git diff against the AI spec.
 *
 * <p><strong>When to use:</strong> after a green build. Approve only when the diff matches the
 * spec and tests cover the acceptance criteria.
 *
 * <p><strong>Example:</strong> {@code REQUEST_CHANGES} when the 404 path has no test.
 */
public interface PrReviewerAgent {

    /**
     * Reviews the uncommitted diff.
     *
     * @param aiSpec spec the change must satisfy
     * @param gitDiff unified diff
     * @param conventions stack conventions
     * @return approve or request changes
     */
    @UserMessage(
            """
            AI spec:
            {{aiSpec}}

            Git diff:
            {{gitDiff}}

            Conventions:
            {{conventions}}

            Return JSON for ReviewVerdict with decision (APPROVE or REQUEST_CHANGES),
            findings (severity, file, rationale), and blockingCount.
            Put that JSON object in the assistant message content, not only in reasoning.
            """)
    @Agent(description = "Reviews the git diff against the AI spec", outputKey = StateKeys.REVIEW_VERDICT)
    ReviewVerdict review(
            @V(StateKeys.AI_SPEC) AiSpec aiSpec,
            @V(StateKeys.GIT_DIFF) String gitDiff,
            @V(StateKeys.CONVENTIONS) String conventions);
}
