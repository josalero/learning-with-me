package dev.mytechprofile.langgraph4j.cursor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Copies the ping seed and runs the allowlisted Gradle test.
 */
final class SeedWorkspace {

    private SeedWorkspace() {}

    static void copySeed(Path seedDir, Path work) throws IOException {
        if (Files.exists(work)) {
            try (Stream<Path> walk = Files.walk(work)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best-effort cleanup before recopy
                    }
                });
            }
        }
        try (Stream<Path> walk = Files.walk(seedDir)) {
            walk.forEach(source -> {
                Path relative = seedDir.relativize(source);
                if (relative.toString().isBlank()) {
                    return;
                }
                String name = relative.toString().replace('\\', '/');
                if (name.startsWith("build/") || name.contains("/.gradle/") || name.startsWith(".gradle")) {
                    return;
                }
                Path target = work.resolve(relative);
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Could not copy seed file " + source, ex);
                }
            });
        }
    }

    static ProcessResult runGradleTest(Path gradleWrapper, Path work) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                gradleWrapper.toAbsolutePath().toString(),
                "-p",
                work.toAbsolutePath().toString(),
                "test",
                "--no-daemon");
        builder.redirectErrorStream(true);
        builder.directory(work.toFile());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(124, output + "\nGradle test timed out after 3 minutes");
        }
        return new ProcessResult(process.exitValue(), output);
    }

    record ProcessResult(int exitCode, String output) {}
}
