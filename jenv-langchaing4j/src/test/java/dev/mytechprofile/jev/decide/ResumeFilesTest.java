package dev.mytechprofile.jev.decide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void readsATextFileInsideTheWorkingDirectory() throws IOException {
        Path file = Path.of("build", "test-resume.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "4 years of experience.");
        try {
            assertThat(ResumeFiles.read(file)).contains("4 years");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsAPathThatLeavesTheWorkingDirectory() {
        assertThatThrownBy(() -> ResumeFiles.read(Path.of("..", "secret.txt")))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("Resume path must stay inside the working directory");
    }

    @Test
    void rejectsAnEmptyFile() throws IOException {
        Path file = Path.of("build", "empty-resume.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "   ");
        try {
            assertThatThrownBy(() -> ResumeFiles.read(file))
                    .isInstanceOf(InvalidResumeException.class)
                    .hasMessage("Resume file is empty");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsAMissingFile() {
        assertThatThrownBy(() -> ResumeFiles.read(tempDir.resolve("missing.txt")))
                .isInstanceOf(InvalidResumeException.class);
    }

    @Test
    void rejectsASymlinkThatPointsOutsideTheWorkingDirectory() throws IOException {
        Path outside = Files.createTempFile("resume-outside", ".txt");
        Path link = Path.of("build", "outside-resume-link.txt");
        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);
        try {
            Files.writeString(outside, "10 years of experience.");
            Files.createSymbolicLink(link, outside);
            assertThatThrownBy(() -> ResumeFiles.read(link))
                    .isInstanceOf(InvalidResumeException.class)
                    .hasMessage("Resume path must stay inside the working directory");
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void invalidPathStringUsesAFixedMessage() {
        assertThatThrownBy(() -> ResumeFiles.read("leak-marker\u0000path"))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessage("Resume path is invalid")
                .hasMessageNotContaining("leak-marker");
    }
}
