package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * Product Owner agent that turns a feature request into a {@link FeatureBrief}.
 *
 * <p><strong>When to use:</strong> first step of a run, or after a stakeholder rejection.
 *
 * <p><strong>Example:</strong> input {@code "Return 404 for unknown users"} yields numbered
 * Given/When/Then criteria.
 */
public interface ProductOwnerAgent {

    /**
     * Writes a feature brief from the request and any stakeholder follow-ups.
     *
     * @param featureRequest product request in natural language
     * @param stakeholderFollowUps empty on the first pass
     * @return structured brief with acceptance criteria
     */
    @UserMessage(
            """
            Feature request:
            {{featureRequest}}

            Stakeholder follow-ups from a previous cycle (may be empty):
            {{stakeholderFollowUps}}

            Return JSON for FeatureBrief with title, problem, userStories, acceptanceCriteria
            (id, given, when, then), outOfScope, and priority.
            """)
    @Agent(description = "Defines the feature and acceptance criteria", outputKey = StateKeys.FEATURE_BRIEF)
    FeatureBrief defineFeature(
            @V(StateKeys.FEATURE_REQUEST) String featureRequest,
            @V(StateKeys.STAKEHOLDER_FOLLOW_UPS) String stakeholderFollowUps);
}
