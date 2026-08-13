package dev.mytechprofile.sdlc.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Path-jailed file operations against a repository root.
 *
 * <p>Sample: {@code workspace.read("src/main/java/User.java")} returns file text.
 */
public interface WorkspacePort {

    Path root();

    List<String> listFiles(String glob);

    String readFile(String relativePath);

    void writeFile(String relativePath, String content);

    void deleteFile(String relativePath);
}
