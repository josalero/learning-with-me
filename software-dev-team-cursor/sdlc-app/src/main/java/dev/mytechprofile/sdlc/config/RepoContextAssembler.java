package dev.mytechprofile.sdlc.config;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the bounded repository context that always goes into prompts.
 *
 * <p><strong>When to use:</strong> before Tech Lead, Developer, Reviewer, or QA runs.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * String tree = assembler.fileTree(workspace, profile);
 * String conventions = assembler.conventions(profile);
 * }</pre>
 */
public final class RepoContextAssembler {

    static final int MAX_TREE_LINES = 400;

    /**
     * Lists source files matching the project globs, with sizes.
     *
     * @param workspace jailed workspace
     * @param profile technology profile
     * @return newline-separated {@code path (N bytes)} lines
     */
    public String fileTree(WorkspacePort workspace, ProjectProfile profile) {
        List<String> lines = new ArrayList<>();
        for (String glob : profile.sourceGlobs()) {
            for (String relative : workspace.listFiles(glob)) {
                Path file = workspace.root().resolve(relative);
                long size = 0L;
                try {
                    if (Files.isRegularFile(file)) {
                        size = Files.size(file);
                    }
                } catch (IOException ignored) {
                    size = 0L;
                }
                lines.add(relative + " (" + size + " bytes)");
                if (lines.size() >= MAX_TREE_LINES) {
                    lines.add("... truncated ...");
                    return String.join("\n", lines);
                }
            }
        }
        return lines.isEmpty() ? "(no source files matched sourceGlobs)" : String.join("\n", lines);
    }

    /**
     * Loads the conventions file when it exists.
     *
     * @param profile technology profile
     * @return conventions text or a short fallback
     */
    public String conventions(ProjectProfile profile) {
        Path conventions = profile.conventions();
        if (conventions == null || !Files.isRegularFile(conventions)) {
            return "(no conventions file)";
        }
        try {
            return Files.readString(conventions);
        } catch (IOException ex) {
            return "(failed to read conventions: " + ex.getMessage() + ")";
        }
    }
}
