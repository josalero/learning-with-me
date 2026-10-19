package dev.mytechprofile.sdlc.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Local git operations. Implementations must never push.
 *
 * <p>Sample: {@code vcs.createBranch(repo, "feature/unknown-user-404")}.
 */
public interface VersionControlPort {

    void initIfNeeded(Path repo);

    void commitAll(Path repo, String message);

    void createBranch(Path repo, String branch);

    String currentBranch(Path repo);

    /**
     * Returns a unified diff of uncommitted work versus {@code HEAD}, including untracked files.
     *
     * @param repo repository root
     * @return diff text, empty when clean
     */
    String workingTreeDiff(Path repo);

    String diffAgainst(Path repo, String baseRef);

    boolean hasChanges(Path repo);

    /**
     * Returns relative source paths that differ from {@code HEAD}, including untracked files.
     *
     * <p>Sample: after the Developer edits {@code UserController.java}, this returns that path
     * and omits {@code build/} reports.
     *
     * @param repo repository root
     * @return changed source paths, empty when clean
     */
    List<String> changedSourcePaths(Path repo);
}
