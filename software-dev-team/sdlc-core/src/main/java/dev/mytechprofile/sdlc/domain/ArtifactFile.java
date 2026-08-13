package dev.mytechprofile.sdlc.domain;

/**
 * Numbered run artifacts written under {@code runs/<runId>/}.
 *
 * <p><strong>When to use:</strong> persist pipeline products and render the Results tab. Filenames
 * stay stable so {@code docs/how-to.md} and {@code docs/sample-specs/} stay in sync.
 *
 * <p><strong>Example:</strong> {@code artifacts.writeJson(runId, ArtifactFile.FEATURE_BRIEF.fileName(),
 * brief)}.
 */
public enum ArtifactFile {
    FEATURE_BRIEF("01-feature-brief.json", StateKeys.FEATURE_BRIEF, "What are we building?"),
    AI_SPEC("02-ai-spec.json", StateKeys.AI_SPEC, "Which files and tests?"),
    CHANGE_SUMMARY("03-change-summary.json", StateKeys.CHANGE_SUMMARY, "What did the Developer touch?"),
    BUILD_RESULT("04-build-result.json", StateKeys.BUILD_RESULT, "Did the allowlisted test command pass?"),
    REVIEW_VERDICT("05-review-verdict.json", StateKeys.REVIEW_VERDICT, "Did the diff match the spec?"),
    QA_VERDICT("06-qa-verdict.json", StateKeys.QA_VERDICT, "Did every AC have evidence?"),
    PULL_REQUEST("07-pull-request.diff", StateKeys.GIT_DIFF, "The local PR diff"),
    STAKEHOLDER_DECISION("08-stakeholder-decision.json", StateKeys.STAKEHOLDER_DECISION, "Ship or send back?");

    private final String fileName;
    private final String stateKey;
    private final String question;

    ArtifactFile(String fileName, String stateKey, String question) {
        this.fileName = fileName;
        this.stateKey = stateKey;
        this.question = question;
    }

    /**
     * Returns the file name under {@code runs/<runId>/}.
     *
     * @return numbered file name
     */
    public String fileName() {
        return fileName;
    }

    /**
     * Returns the agentic-scope key this artifact snapshots.
     *
     * @return {@link StateKeys} constant
     */
    public String stateKey() {
        return stateKey;
    }

    /**
     * Returns the operator-facing question this file answers.
     *
     * @return short question
     */
    public String question() {
        return question;
    }
}
