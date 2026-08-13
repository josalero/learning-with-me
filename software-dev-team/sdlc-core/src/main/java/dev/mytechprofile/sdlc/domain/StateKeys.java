package dev.mytechprofile.sdlc.domain;

/**
 * Agentic-scope keys shared by prompts, agents, and the pipeline.
 *
 * <p><strong>When to use:</strong> every {@code @V}, {@code outputKey}, and {@code
 * AgenticScope} read/write must use these constants. The table lives in {@code
 * docs/agent-state.md}.
 *
 * <p><strong>Example:</strong> {@code scope.writeState(StateKeys.FEATURE_BRIEF, brief)}.
 */
public final class StateKeys {

    public static final String FEATURE_REQUEST = "featureRequest";
    public static final String FEATURE_BRIEF = "featureBrief";
    public static final String AI_SPEC = "aiSpec";
    public static final String CHANGE_SUMMARY = "changeSummary";
    public static final String BUILD_RESULT = "buildResult";
    public static final String BUILD_FEEDBACK = "buildFeedback";
    public static final String BUILD_OUTPUT = "buildOutput";
    public static final String REVIEW_VERDICT = "reviewVerdict";
    public static final String REVIEW_FEEDBACK = "reviewFeedback";
    public static final String QA_VERDICT = "qaVerdict";
    public static final String QA_FEEDBACK = "qaFeedback";
    public static final String STAKEHOLDER_DECISION = "stakeholderDecision";
    public static final String STAKEHOLDER_FOLLOW_UPS = "stakeholderFollowUps";
    public static final String FILE_TREE = "fileTree";
    public static final String CONVENTIONS = "conventions";
    public static final String GIT_DIFF = "gitDiff";
    public static final String RUN_ID = "runId";
    public static final String BRANCH = "branch";

    private StateKeys() {}
}
