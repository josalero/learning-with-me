package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.QaVerdict;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * Stakeholder agent that approves or rejects the delivered feature.
 *
 * <p><strong>When to use:</strong> after QA when {@code stakeholderMode} is {@code AGENT}. Human
 * mode uses a HITL gate instead.
 *
 * <p><strong>Example:</strong> {@code APPROVED} when the brief, spec, and QA all line up.
 */
public interface StakeholderAgent {

    /**
     * Decides whether the feature is done.
     *
     * @param featureBrief original brief
     * @param aiSpec spec
     * @param qaVerdict QA score
     * @param changeSummary files touched
     * @return approved or rejected with follow-ups
     */
    @UserMessage(
            """
            Feature brief:
            {{featureBrief}}

            AI spec:
            {{aiSpec}}

            QA verdict:
            {{qaVerdict}}

            Change summary:
            {{changeSummary}}

            Return JSON for StakeholderDecision with decision (APPROVED or REJECTED),
            reasons, and followUps.
            """)
    @Agent(description = "Approves or rejects the feature", outputKey = StateKeys.STAKEHOLDER_DECISION)
    StakeholderDecision decide(
            @V(StateKeys.FEATURE_BRIEF) FeatureBrief featureBrief,
            @V(StateKeys.AI_SPEC) AiSpec aiSpec,
            @V(StateKeys.QA_VERDICT) QaVerdict qaVerdict,
            @V(StateKeys.CHANGE_SUMMARY) ChangeSummary changeSummary);
}
