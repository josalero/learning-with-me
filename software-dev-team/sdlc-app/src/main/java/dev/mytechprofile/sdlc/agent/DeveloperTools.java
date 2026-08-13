package dev.mytechprofile.sdlc.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.mytechprofile.sdlc.port.WorkspacePort;
import java.util.List;

/**
 * Path-jailed file tools for the Developer agent. Tests run in the build gate, not here.
 *
 * <p><strong>When to use:</strong> bind this object with {@code .tools(developerTools)} on the
 * Developer agent builder.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * tools.readFile("src/main/java/UserController.java");
 * tools.writeFile("src/test/java/UserControllerTest.java", source);
 * }</pre>
 */
public final class DeveloperTools {

    static final int MAX_TOOL_CHARS = 12_000;

    private final WorkspacePort workspace;

    /**
     * Creates tools bound to one project workspace.
     *
     * @param workspace jailed files
     */
    public DeveloperTools(WorkspacePort workspace) {
        this.workspace = workspace;
    }

    /**
     * Lists files matching a glob relative to the repo root.
     *
     * @param glob glob such as {@code src/main/**}/*.java
     * @return matching relative paths
     */
    @Tool("List repository files matching a glob relative to the repo root")
    public List<String> listFiles(String glob) {
        return workspace.listFiles(glob);
    }

    /**
     * Reads a text file inside the jail.
     *
     * @param relativePath path relative to the repo root
     * @return file contents, truncated if huge
     */
    @Tool("Read a UTF-8 text file relative to the repo root")
    public String readFile(String relativePath) {
        return truncate(workspace.readFile(relativePath));
    }

    /**
     * Writes a text file inside the jail, creating parent directories.
     *
     * @param relativePath path relative to the repo root
     * @param content new file contents
     * @return confirmation
     */
    @Tool("Write a UTF-8 text file relative to the repo root")
    public String writeFile(String relativePath, String content) {
        workspace.writeFile(relativePath, content);
        return "wrote " + relativePath;
    }

    /**
     * Deletes a file inside the jail if it exists.
     *
     * @param relativePath path relative to the repo root
     * @return confirmation
     */
    @Tool("Delete a file relative to the repo root")
    public String deleteFile(String relativePath) {
        workspace.deleteFile(relativePath);
        return "deleted " + relativePath;
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TOOL_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TOOL_CHARS - 3) + "...";
    }
}
