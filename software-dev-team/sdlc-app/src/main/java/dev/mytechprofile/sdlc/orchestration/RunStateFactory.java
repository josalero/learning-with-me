package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.config.RepoContextAssembler;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.BuildResult;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.QaVerdict;
import dev.mytechprofile.sdlc.domain.ReviewVerdict;
import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds and refreshes agentic-scope keys for one run.
 *
 * <p><strong>When to use:</strong> {@link SdlcOrchestrator} before {@code invoke}, and each loop
 * {@code beforeCall}.
 *
 * <p><strong>Example:</strong> {@code state.initialState(command, project, workspace)}.
 */
public final class RunStateFactory {

    private static final Logger log = LoggerFactory.getLogger(RunStateFactory.class);
    static final int MAX_GIT_DIFF_CHARS = 20_000;

    private final RepoContextAssembler assembler;
    private final VersionControlPort git;

    /**
     * Creates a factory that can rebuild file trees and diffs.
     *
     * @param assembler file tree and conventions
     * @param git working-tree diff
     */
    public RunStateFactory(RepoContextAssembler assembler, VersionControlPort git) {
        this.assembler = assembler;
        this.git = git;
    }

    /**
     * Returns the initial scope map for a feature run.
     *
     * @param command feature text
     * @param project technology profile
     * @param workspace seeded repo
     * @return mutable state map
     */
    public Map<String, Object> initialState(RunCommand command, ProjectProfile project, WorkspacePort workspace) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(StateKeys.FEATURE_REQUEST, command.featureRequest());
        inputs.put(StateKeys.STAKEHOLDER_FOLLOW_UPS, "");
        inputs.put(StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(command.featureRequest()));
        inputs.put(StateKeys.AI_SPEC, AiSpec.empty());
        inputs.put(StateKeys.CHANGE_SUMMARY, ChangeSummary.none());
        inputs.put(StateKeys.BUILD_RESULT, BuildResult.none());
        inputs.put(StateKeys.BUILD_FEEDBACK, "");
        inputs.put(StateKeys.BUILD_OUTPUT, "");
        inputs.put(StateKeys.REVIEW_FEEDBACK, "");
        inputs.put(StateKeys.QA_FEEDBACK, "");
        inputs.put(StateKeys.REVIEW_VERDICT, new ReviewVerdict(ReviewVerdict.REQUEST_CHANGES, List.of(), 0));
        inputs.put(StateKeys.QA_VERDICT, new QaVerdict(QaVerdict.FAIL, 0, List.of(), List.of()));
        inputs.put(StateKeys.STAKEHOLDER_DECISION, StakeholderDecision.rejected("pending"));
        inputs.put(StateKeys.FILE_TREE, assembler.fileTree(workspace, project));
        inputs.put(StateKeys.CONVENTIONS, assembler.conventions(project));
        inputs.put(StateKeys.GIT_DIFF, "");
        return inputs;
    }

    /**
     * Refreshes file tree, conventions, and git diff on the scope.
     *
     * @param context run handles
     * @param scope live agentic scope
     */
    public void refresh(RunContext context, AgenticScope scope) {
        scope.writeState(StateKeys.FILE_TREE, assembler.fileTree(context.workspace(), context.project()));
        scope.writeState(StateKeys.CONVENTIONS, assembler.conventions(context.project()));
        String incoming = truncateDiff(git.workingTreeDiff(context.workspace().root()));
        Object current = scope.readState(StateKeys.GIT_DIFF);
        scope.writeState(StateKeys.GIT_DIFF, nextGitDiff(current instanceof String text ? text : "", incoming));
        BuildResult build = read(scope, StateKeys.BUILD_RESULT, BuildResult.none());
        if (!scope.hasState(StateKeys.BUILD_OUTPUT)) {
            scope.writeState(StateKeys.BUILD_OUTPUT, build.truncatedOutput());
        }
        if (!scope.hasState(StateKeys.BUILD_FEEDBACK)) {
            scope.writeState(StateKeys.BUILD_FEEDBACK, build.truncatedOutput());
        }
        if (!scope.hasState(StateKeys.REVIEW_FEEDBACK)) {
            scope.writeState(StateKeys.REVIEW_FEEDBACK, "");
        }
        if (!scope.hasState(StateKeys.QA_FEEDBACK)) {
            scope.writeState(StateKeys.QA_FEEDBACK, "");
        }
    }

    /**
     * Reads a typed value from the scope, or {@code fallback} when missing or wrongly typed.
     *
     * @param scope agentic scope
     * @param key {@link StateKeys} constant
     * @param fallback value used when the key is absent or the type does not match
     * @param <T> expected type
     * @return stored value or fallback
     */
    @SuppressWarnings("unchecked")
    public static <T> T read(AgenticScope scope, String key, T fallback) {
        Object value = scope.readState(key);
        if (value == null) {
            return fallback;
        }
        if (fallback.getClass().isInstance(value)) {
            return (T) value;
        }
        log.warn(
                "State key '{}' held {} but {} was expected. Using fallback. Check StateKeys and the agent outputKey.",
                key,
                value.getClass().getName(),
                fallback.getClass().getName());
        return fallback;
    }

    static String truncateDiff(String diff) {
        String text = diff == null ? "" : diff;
        if (text.length() <= MAX_GIT_DIFF_CHARS) {
            return text;
        }
        return text.substring(0, MAX_GIT_DIFF_CHARS - 3) + "...\n[diff truncated for the reviewer prompt]";
    }

    /**
     * Keeps the last non-empty feature diff after a commit wipes the working tree.
     *
     * @param current value already on the scope
     * @param incoming {@code git diff HEAD} after intent-to-add
     * @return {@code incoming} when it has content, otherwise {@code current}
     */
    static String nextGitDiff(String current, String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return current == null ? "" : current;
    }
}
