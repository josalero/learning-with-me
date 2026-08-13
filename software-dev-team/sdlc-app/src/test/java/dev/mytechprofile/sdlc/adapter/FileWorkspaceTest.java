package dev.mytechprofile.sdlc.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mytechprofile.sdlc.port.PathJailException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileWorkspaceTest {

    @TempDir
    Path temp;

    @Test
    void writeFile_whenPathUsesParentSegments_throwsPathJailException() throws Exception {
        Path root = Files.createDirectories(temp.resolve("repo"));
        FileWorkspace workspace = new FileWorkspace(root);

        assertThatThrownBy(() -> workspace.writeFile("../escape.txt", "nope"))
                .isInstanceOf(PathJailException.class)
                .hasMessageContaining("escapes workspace jail");
    }

    @Test
    void writeFile_whenPathIsAbsolute_throwsPathJailException() {
        FileWorkspace workspace = new FileWorkspace(temp.resolve("repo"));

        assertThatThrownBy(() -> workspace.writeFile("/tmp/absolute.txt", "nope"))
                .isInstanceOf(PathJailException.class)
                .hasMessageContaining("Absolute paths");
    }

    @Test
    void readFile_whenSymlinkEscapesRoot_throwsPathJailException() throws Exception {
        Path outside = Files.writeString(temp.resolve("secret.txt"), "secret");
        Path root = Files.createDirectories(temp.resolve("repo"));
        Files.createSymbolicLink(root.resolve("link.txt"), outside);
        FileWorkspace workspace = new FileWorkspace(root);

        assertThatThrownBy(() -> workspace.readFile("link.txt"))
                .isInstanceOf(PathJailException.class)
                .hasMessageContaining("Symlink escapes");
    }

    @Test
    void writeFile_whenPathStaysInside_roundTrips() {
        FileWorkspace workspace = new FileWorkspace(temp.resolve("repo"));
        workspace.writeFile("src/User.java", "class User {}");
        assertThat(workspace.readFile("src/User.java")).isEqualTo("class User {}");
    }

    @Test
    void listFiles_skipsGeneratedDirectories() throws Exception {
        Path root = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/User.java"), "class User {}");
        Files.createDirectories(root.resolve("build/reports/problems"));
        Files.writeString(root.resolve("build/reports/problems/problems-report.html"), "<html></html>");
        Files.createDirectories(root.resolve("node_modules/left-pad"));
        Files.writeString(root.resolve("node_modules/left-pad/index.js"), "module.exports = 1;");

        assertThat(new FileWorkspace(root).listFiles("**/*")).containsExactly("src/main/java/User.java");
    }

    @Test
    void readFile_whenPathIsGeneratedOutput_explainsWhatToReadInstead() throws Exception {
        Path root = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectories(root.resolve("build/reports"));
        Files.writeString(root.resolve("build/reports/problems-report.html"), "<html></html>");
        FileWorkspace workspace = new FileWorkspace(root);

        assertThatThrownBy(() -> workspace.readFile("build/reports/problems-report.html"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated output is not readable")
                .hasMessageContaining("src/main/**");
    }

    @Test
    void readFile_whenFileIsTooLarge_saysWhichFileAndWhatToDo() throws Exception {
        Path root = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/Huge.java"), "x".repeat(FileWorkspace.MAX_FILE_CHARS + 1));
        FileWorkspace workspace = new FileWorkspace(root);

        assertThatThrownBy(() -> workspace.readFile("src/Huge.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("src/Huge.java")
                .hasMessageContaining("Read a single source file instead");
    }
}
