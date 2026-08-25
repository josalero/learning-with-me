package dev.mytechprofile.sdlc.adapter;

import dev.mytechprofile.sdlc.port.VersionControlPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local git CLI adapter. Never pushes.
 *
 * <p>Sample: {@code git.createBranch(repo, "feature/unknown-user-404")}. Diffs omit {@code build/}
 * and {@code .gradle} so the PR Reviewer sees source, not Gradle reports.
 */
public final class LocalGit implements VersionControlPort {

    private static final Logger log = LoggerFactory.getLogger(LocalGit.class);

    @Override
    public void initIfNeeded(Path repo) {
        if (!repo.resolve(".git").toFile().exists()) {
            exec(repo, List.of("git", "init"));
            exec(repo, List.of("git", "config", "user.email", "sdlc-bot@local"));
            exec(repo, List.of("git", "config", "user.name", "SDLC Bot"));
        }
        prepareIgnoreRules(repo);
    }

    @Override
    public void commitAll(Path repo, String message) {
        prepareIgnoreRules(repo);
        // .gitignore is the filter. Do not pass :(exclude) to `git add` — Git exits 1 when those
        // paths exist and are ignored, which aborts the whole SDLC pipeline.
        exec(repo, List.of("git", "add", "-A"), 0, 1);
        CommandOutput status = exec(repo, List.of("git", "status", "--porcelain"));
        if (status.stdout().isBlank()) {
            log.debug("No git changes to commit in {}", repo);
            return;
        }
        exec(repo, List.of("git", "commit", "-m", message));
    }

    @Override
    public void createBranch(Path repo, String branch) {
        exec(repo, List.of("git", "checkout", "-B", branch));
    }

    @Override
    public String currentBranch(Path repo) {
        return exec(repo, List.of("git", "rev-parse", "--abbrev-ref", "HEAD"))
                .stdout()
                .trim();
    }

    @Override
    public String workingTreeDiff(Path repo) {
        prepareIgnoreRules(repo);
        // Intent-to-add makes untracked source files show up in `git diff HEAD`. Without this, a
        // new class like ProblemDetail.java is invisible to the PR Reviewer while still compiling.
        exec(repo, List.of("git", "add", "-N", "--", "."), 0, 1);
        return exec(repo, withSourcePathspecs("git", "diff", "HEAD"), 0, 1).stdout();
    }

    @Override
    public String diffAgainst(Path repo, String baseRef) {
        prepareIgnoreRules(repo);
        String ref = baseRef == null || baseRef.isBlank() ? "HEAD" : baseRef;
        exec(repo, List.of("git", "add", "-N", "--", "."), 0, 1);
        return exec(repo, withSourcePathspecs("git", "diff", ref), 0, 1).stdout();
    }

    @Override
    public boolean hasChanges(Path repo) {
        prepareIgnoreRules(repo);
        return !exec(repo, List.of("git", "status", "--porcelain")).stdout().isBlank();
    }

    private static void prepareIgnoreRules(Path repo) {
        ensureGitignore(repo);
        unstageGenerated(repo);
    }

    private static void ensureGitignore(Path repo) {
        Path ignore = repo.resolve(".gitignore");
        if (Files.exists(ignore)) {
            return;
        }
        try {
            Files.createDirectories(repo);
            Files.writeString(ignore, GeneratedTrees.gitignore(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write .gitignore in " + repo, ex);
        }
    }

    private static void unstageGenerated(Path repo) {
        if (!Files.exists(repo.resolve(".git"))) {
            return;
        }
        for (String name : GeneratedTrees.DIRECTORY_NAMES) {
            if (".git".equals(name)) {
                continue;
            }
            exec(repo, List.of("git", "rm", "-r", "--cached", "--ignore-unmatch", "-q", "--", name), 0, 1);
        }
    }

    private static List<String> withSourcePathspecs(String... gitCommand) {
        List<String> argv = new ArrayList<>(List.of(gitCommand));
        argv.add("--");
        argv.addAll(GeneratedTrees.sourcePathspecs());
        return argv;
    }

    private static CommandOutput exec(Path repo, List<String> argv) {
        return exec(repo, argv, 0);
    }

    private static CommandOutput exec(Path repo, List<String> argv, int... allowedExitCodes) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(repo.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("git command timed out: " + argv);
            }
            int exit = process.exitValue();
            if (!allowed(exit, allowedExitCodes)) {
                throw new IllegalStateException("git command failed (" + exit + "): " + argv + "\n" + stdout);
            }
            return new CommandOutput(stdout);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git command interrupted: " + argv, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("git command failed to start: " + argv, ex);
        }
    }

    private static boolean allowed(int exit, int... allowedExitCodes) {
        for (int allowed : allowedExitCodes) {
            if (exit == allowed) {
                return true;
            }
        }
        return false;
    }

    private record CommandOutput(String stdout) {}
}
