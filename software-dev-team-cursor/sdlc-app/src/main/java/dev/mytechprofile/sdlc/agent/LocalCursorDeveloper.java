package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.mytechprofile.sdlc.catalog.RoleSpec;
import dev.mytechprofile.sdlc.config.CursorCliExecutor;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.domain.AiSpec;
import dev.mytechprofile.sdlc.domain.ChangeSummary;
import dev.mytechprofile.sdlc.domain.FeatureBrief;
import dev.mytechprofile.sdlc.domain.StateKeys;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.orchestration.RunContext;
import dev.mytechprofile.sdlc.orchestration.RunStateFactory;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the local Cursor {@code agent} CLI so it writes the seeded workspace.
 *
 * <p><strong>When to use:</strong> live L2 and review/QA rework when {@link
 * SdlcProperties#useLocalCursorDeveloper()} is true. Ask-mode roles stay on {@code
 * CursorCliChatModel}. Offline runs keep {@link DeveloperTools}.
 *
 * <p><strong>Example:</strong> {@code factory.subAgents(developer.asAction(role, context),
 * buildGate)}.
 */
public final class LocalCursorDeveloper {

    static final Duration IMPLEMENT_TIMEOUT_FLOOR = Duration.ofMinutes(10);
    static final int NOTES_LIMIT = 2_000;

    private final SdlcProperties properties;
    private final CursorCliExecutor executor;
    private final VersionControlPort git;

    /**
     * Creates the local Cursor implementer.
     *
     * @param properties CLI binary, key, models, and timeouts
     * @param executor process runner (fake this in tests)
     * @param git working-tree change list
     */
    public LocalCursorDeveloper(SdlcProperties properties, CursorCliExecutor executor, VersionControlPort git) {
        this.properties = properties;
        this.executor = executor;
        this.git = git;
    }

    /**
     * Returns an agentic action that edits the workspace and writes {@link ChangeSummary}.
     *
     * @param role developer role (model id comes from YAML)
     * @param context seeded workspace and event listener
     * @return LangChain4j agent action
     */
    public Object asAction(RoleSpec role, RunContext context) {
        return AgenticServices.agentAction(scope -> {
            AiSpec spec = RunStateFactory.read(scope, StateKeys.AI_SPEC, AiSpec.empty());
            FeatureBrief brief = RunStateFactory.read(scope, StateKeys.FEATURE_BRIEF, FeatureBrief.fromRequest(""));
            String fileTree = RunStateFactory.read(scope, StateKeys.FILE_TREE, "");
            String conventions = RunStateFactory.read(scope, StateKeys.CONVENTIONS, "");
            String review = RunStateFactory.read(scope, StateKeys.REVIEW_FEEDBACK, "");
            String qa = RunStateFactory.read(scope, StateKeys.QA_FEEDBACK, "");
            String build = RunStateFactory.read(scope, StateKeys.BUILD_FEEDBACK, "");
            ChangeSummary summary =
                    implement(role, context.workspace().root(), spec, brief, fileTree, conventions, review, qa, build);
            scope.writeState(StateKeys.CHANGE_SUMMARY, summary);
            context.events()
                    .accept(new StepEvent(
                            "developer",
                            "cursor-implement",
                            spec.summary(),
                            summary.filesTouched().size() + " files",
                            0L));
        });
    }

    /**
     * Invokes the local Cursor agent against {@code workspace} and builds a change summary from git.
     *
     * @param role developer role
     * @param workspace seeded repository root
     * @param spec current AI spec
     * @param brief feature brief
     * @param fileTree filtered tree
     * @param conventions stack conventions
     * @param reviewFeedback previous review or empty
     * @param qaFeedback previous QA or empty
     * @param buildFeedback last build output or empty
     * @return files git sees after the agent exits
     */
    public ChangeSummary implement(
            RoleSpec role,
            Path workspace,
            AiSpec spec,
            FeatureBrief brief,
            String fileTree,
            String conventions,
            String reviewFeedback,
            String qaFeedback,
            String buildFeedback) {
        if (properties.cursorApiKey() == null || properties.cursorApiKey().isBlank()) {
            throw new IllegalStateException(
                    "CURSOR_API_KEY is not set. Copy .env.example to .env and add a key from https://cursor.com/dashboard?tab=integrations, or set SDLC_OFFLINE=true");
        }
        Path root = workspace.toAbsolutePath().normalize();
        String prompt = buildPrompt(spec, brief, fileTree, conventions, reviewFeedback, qaFeedback, buildFeedback);
        String stdout = executor.execute(argv(role, root, prompt), root, implementTimeout());
        List<String> files = git.changedSourcePaths(root);
        String rationale = files.isEmpty()
                ? "Cursor agent finished but the working tree has no source changes"
                : "Cursor agent implemented the spec";
        return new ChangeSummary(files, rationale, truncateNotes(stdout));
    }

    List<String> argv(RoleSpec role, Path workspace, String prompt) {
        List<String> command = new ArrayList<>();
        command.add(properties.cursorCliCommand());
        command.add("-p");
        command.add("--output-format");
        command.add("text");
        command.add("--trust");
        command.add("--workspace");
        command.add(workspace.toString());
        command.add("--force");
        command.add("--model");
        command.add(role.model());
        if (properties.cursorApiKey() != null && !properties.cursorApiKey().isBlank()) {
            command.add("--api-key");
            command.add(properties.cursorApiKey());
        }
        command.add(prompt);
        return List.copyOf(command);
    }

    Duration implementTimeout() {
        Duration configured = properties.llmTimeout();
        return configured.compareTo(IMPLEMENT_TIMEOUT_FLOOR) >= 0 ? configured : IMPLEMENT_TIMEOUT_FLOOR;
    }

    static String buildPrompt(
            AiSpec spec,
            FeatureBrief brief,
            String fileTree,
            String conventions,
            String reviewFeedback,
            String qaFeedback,
            String buildFeedback) {
        return """
                You are the Developer on a local software team. Edit files in this workspace only.

                Rules:
                - Implement the spec and add or update tests.
                - Do not commit, push, or change git remotes.
                - Do not print API keys, tokens, or secrets.
                - Do not run the project test suite; the orchestrator runs the allowlisted tests after you finish.
                - Do not invent files that are not needed.
                - When finished, print a short summary of files you changed.

                Feature brief:
                %s

                AI spec:
                %s

                Repository file tree:
                %s

                Conventions:
                %s

                Review feedback (may be empty):
                %s

                QA feedback (may be empty):
                %s

                Last build output (may be empty):
                %s
                """
                .formatted(
                        brief,
                        spec,
                        nullToEmpty(fileTree),
                        nullToEmpty(conventions),
                        nullToEmpty(reviewFeedback),
                        nullToEmpty(qaFeedback),
                        nullToEmpty(buildFeedback));
    }

    private static String truncateNotes(String stdout) {
        String text = stdout == null ? "" : stdout.trim();
        if (text.length() <= NOTES_LIMIT) {
            return text;
        }
        return text.substring(0, NOTES_LIMIT - 3) + "...";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
