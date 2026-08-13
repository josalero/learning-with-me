package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.util.List;

/**
 * Read-only file tools for the Tech Lead so specs reference real paths.
 *
 * <p><strong>When to use:</strong> bind on the Tech Lead agent builder.
 *
 * <p><strong>Example:</strong> {@code tools.readFile("src/main/java/UserController.java")}.
 */
public final class ReadOnlyRepoTools {

    static final int MAX_TOOL_CHARS = 12_000;

    private final WorkspacePort workspace;

    /**
     * Creates read-only tools for {@code workspace}.
     *
     * @param workspace jailed files
     */
    public ReadOnlyRepoTools(WorkspacePort workspace) {
        this.workspace = workspace;
    }

    /**
     * Lists files matching a glob.
     *
     * @param glob glob relative to the repo root
     * @return matching paths
     */
    @Tool("List repository files matching a glob relative to the repo root")
    public List<String> listFiles(String glob) {
        return workspace.listFiles(glob);
    }

    /**
     * Reads a text file inside the jail.
     *
     * @param relativePath path relative to the repo root
     * @return file contents
     */
    @Tool("Read a UTF-8 text file relative to the repo root")
    public String readFile(String relativePath) {
        String text = workspace.readFile(relativePath);
        if (text.length() <= MAX_TOOL_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TOOL_CHARS - 3) + "...";
    }
}
