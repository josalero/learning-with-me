package dev.mytechprofile.sdlc.config;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.port.VersionControlPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies a seed tree into the workspace and creates the initial git commit when needed.
 *
 * <p><strong>When to use:</strong> at the start of a run, before any agent writes files.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * seeder.ensureWorkspace(project);
 * }</pre>
 */
public final class WorkspaceSeeder {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSeeder.class);

    private final VersionControlPort git;

    /**
     * Creates a seeder that uses {@code git} for the initial commit.
     *
     * @param git local git port
     */
    public WorkspaceSeeder(VersionControlPort git) {
        this.git = git;
    }

    /**
     * Ensures {@code profile.repoPath()} exists and is a git repository.
     *
     * @param profile technology profile whose seed and repoPath are used
     */
    public void ensureWorkspace(ProjectProfile profile) {
        Path repo = profile.repoPath();
        try {
            if (!Files.exists(repo) || isEmpty(repo)) {
                log.info("Seeding workspace {} from {}", repo, profile.seed());
                Files.createDirectories(repo.getParent());
                copyDirectory(profile.seed(), repo);
            }
            git.initIfNeeded(repo);
            if (!git.hasChanges(repo) && !Files.exists(repo.resolve(".git"))) {
                git.initIfNeeded(repo);
            }
            git.commitAll(repo, "chore: seed " + profile.id());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to seed workspace " + repo + " from " + profile.seed(), ex);
        }
    }

    /**
     * Deletes the workspace copy and recopies the seed so a POC run starts from the known gaps.
     *
     * <p><strong>When to use:</strong> dashboard Reset and run. Does not touch {@code seeds/}.
     *
     * <p><strong>Example:</strong> {@code seeder.resetWorkspace(javaUsers)} after a previous run
     * edited {@code UserController.java}.
     *
     * @param profile technology profile whose {@code repoPath} stays under {@code workspace/}
     */
    public void resetWorkspace(ProjectProfile profile) {
        Path repo = profile.repoPath();
        try {
            if (Files.exists(repo)) {
                log.info("Resetting workspace {} from seed {}", repo, profile.seed());
                deleteRecursively(repo);
            }
            ensureWorkspace(profile);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to reset workspace " + repo + " from " + profile.seed(), ex);
        }
    }

    private static boolean isEmpty(Path repo) throws IOException {
        if (!Files.isDirectory(repo)) {
            return true;
        }
        try (var stream = Files.list(repo)) {
            return stream.findFirst().isEmpty();
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
