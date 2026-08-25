package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.QaVerdict;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * QA agent that scores each acceptance criterion against the spec, diff, and test output.
 *
 * <p><strong>When to use:</strong> after PR review. Fail when a criterion has no evidence.
 *
 * <p><strong>Example:</strong> {@code AC-1 PASS} with evidence from {@code unknownIdReturns404}.
 */
public interface QaAgent {

    /**
     * Grades the change against acceptance criteria.
     *
     * @param featureBrief brief with criteria
     * @param aiSpec spec with traceability
     * @param gitDiff unified diff
     * @param buildOutput truncated test output
     * @return pass/fail verdict with per-criterion results
     */
    @UserMessage(
            """
            Feature brief:
            {{featureBrief}}

            AI spec:
            {{aiSpec}}

            Git diff:
            {{gitDiff}}

            Test/build output:
            {{buildOutput}}

            Score every acceptance criterion. Return JSON for QaVerdict with decision
            (PASS or FAIL), score 0-100, results (acceptanceCriterionId, status PASS/FAIL,
            evidence), and missingTests.
            """)
    @Agent(description = "Scores acceptance criteria against spec, diff, and tests", outputKey = StateKeys.QA_VERDICT)
    QaVerdict review(
            @V(StateKeys.FEATURE_BRIEF) FeatureBrief featureBrief,
            @V(StateKeys.AI_SPEC) AiSpec aiSpec,
            @V(StateKeys.GIT_DIFF) String gitDiff,
            @V(StateKeys.BUILD_OUTPUT) String buildOutput);
}
