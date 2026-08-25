package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * Tech Lead agent that writes the AI spec the Developer must execute.
 *
 * <p><strong>When to use:</strong> after a {@code FeatureBrief} exists. May loop until every
 * acceptance criterion has a planned test.
 *
 * <p><strong>Example:</strong> maps {@code AC-1} to {@code unknownIdReturns404}.
 */
public interface TechLeadAgent {

    /**
     * Produces an AI spec grounded in the repository tree.
     *
     * @param featureBrief JSON or string form of the brief
     * @param fileTree filtered source tree
     * @param conventions stack conventions
     * @return spec with files, API contract, and traceability
     */
    @UserMessage(
            """
            Feature brief:
            {{featureBrief}}

            Repository file tree (paths and sizes only):
            {{fileTree}}

            Conventions:
            {{conventions}}

            Use readFile if you need the contents of a listed file.
            Return JSON for AiSpec with summary, filesToChange, apiContract, dataModel,
            testPlan, risks, and traceability (acceptanceCriterionId, plannedTest).
            Every acceptance criterion id MUST appear in traceability.
            """)
    @Agent(description = "Writes the AI spec and AC-to-test traceability", outputKey = StateKeys.AI_SPEC)
    AiSpec writeSpec(
            @V(StateKeys.FEATURE_BRIEF) FeatureBrief featureBrief,
            @V(StateKeys.FILE_TREE) String fileTree,
            @V(StateKeys.CONVENTIONS) String conventions);
}
