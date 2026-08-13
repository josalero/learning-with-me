package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.StateKeys;

/**
 * Full-stack Developer agent that edits files through tools.
 *
 * <p><strong>When to use:</strong> after an AI spec exists, and again when review or QA requests
 * rework.
 *
 * <p><strong>Example:</strong> implements 404 problem details, then returns the files touched.
 */
public interface DeveloperAgent {

    /**
     * Implements the spec (or applies review/QA feedback) by calling file tools.
     *
     * @param aiSpec current spec
     * @param fileTree repository tree
     * @param conventions stack conventions
     * @param reviewFeedback previous review verdict or empty
     * @param qaFeedback previous QA verdict or empty
     * @param buildFeedback previous build output or empty
     * @return files touched and rationale
     */
    @UserMessage(
            """
            AI spec:
            {{aiSpec}}

            Repository file tree:
            {{fileTree}}

            Conventions:
            {{conventions}}

            Review feedback (may be empty):
            {{reviewFeedback}}

            QA feedback (may be empty):
            {{qaFeedback}}

            Last build output (may be empty):
            {{buildFeedback}}

            Use listFiles, readFile, writeFile, and deleteFile.
            Do not run tests; the build gate runs the allowlisted test command after your turn.
            Implement the spec with tests. Do not invent files that are not needed.
            Return JSON for ChangeSummary with filesTouched, rationale, and notes.
            """)
    @Agent(description = "Implements the AI spec by editing the repository", outputKey = StateKeys.CHANGE_SUMMARY)
    ChangeSummary implement(
            @V(StateKeys.AI_SPEC) AiSpec aiSpec,
            @V(StateKeys.FILE_TREE) String fileTree,
            @V(StateKeys.CONVENTIONS) String conventions,
            @V(StateKeys.REVIEW_FEEDBACK) String reviewFeedback,
            @V(StateKeys.QA_FEEDBACK) String qaFeedback,
            @V(StateKeys.BUILD_FEEDBACK) String buildFeedback);
}
