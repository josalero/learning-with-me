package dev.mytechprofile.jev.decide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Reads a UTF-8 resume that stays inside the process working directory.
 *
 * <p>{@code ./gradlew bootRun} sets that directory to the project root, so
 * {@code src/main/resources/resumes/junior.txt} is a legal path.
 * {@code ../secret.txt} is rejected. A symlink inside the directory that
 * points outside it is rejected after {@link Path#toRealPath()}.
 * Files larger than 64KB are rejected. The method does not parse PDF or DOCX.
 */
public final class ResumeFiles {

    static final long MAX_BYTES = 64 * 1024;

    private ResumeFiles() {
    }

    /**
     * Reads one text file from a path string.
     *
     * <p>Example: {@code src/main/resources/resumes/junior.txt}.
     * A path that Java cannot parse, such as one containing a NUL character,
     * fails with {@code Resume path is invalid} and does not echo the input.
     *
     * @param pathText path relative to the working directory
     * @return file text
     * @throws InvalidResumeException when the path is invalid, escapes the working directory,
     *     or the file is missing, larger than 64KB, empty, or unreadable
     */
    public static String read(String pathText) {
        try {
            return read(Path.of(pathText));
        } catch (InvalidPathException ex) {
            throw new InvalidResumeException("Resume path is invalid");
        }
    }

    /**
     * Reads one text file.
     *
     * @param requested path relative to the working directory, or absolute if it still
     *     lies inside that directory after normalization and symlink resolution
     * @return file text, including leading and trailing whitespace other than a fully blank file
     * @throws InvalidResumeException when the path escapes the working directory, the file
     *     is missing, larger than 64KB, empty, or unreadable
     */
    public static String read(Path requested) {
        Path base = Path.of("").toAbsolutePath().normalize();
        Path resolved = base.resolve(requested).normalize();
        if (!resolved.startsWith(base)) {
            throw new InvalidResumeException("Resume path must stay inside the working directory");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new InvalidResumeException("Resume file not found");
        }
        try {
            Path realBase = base.toRealPath();
            Path realResolved = resolved.toRealPath();
            if (!realResolved.startsWith(realBase)) {
                throw new InvalidResumeException("Resume path must stay inside the working directory");
            }
            if (Files.size(realResolved) > MAX_BYTES) {
                throw new InvalidResumeException("Resume file is larger than 64KB");
            }
            String text = Files.readString(realResolved, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                throw new InvalidResumeException("Resume file is empty");
            }
            return text;
        } catch (IOException ex) {
            throw new InvalidResumeException("Resume file could not be read");
        }
    }
}
