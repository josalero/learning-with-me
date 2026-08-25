package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Project catalog row with paths relative to {@code sdlc.home}.
 *
 * <p>Sample: {@code repoPath} is {@code workspace/users-service-java}.
 */
public record ProjectView(
        String id,
        String seed,
        String repoPath,
        String branchPrefix,
        List<String> sourceGlobs,
        String conventions,
        Map<String, List<String>> commands,
        long timeoutSeconds,
        String javaHome) {

    /**
     * Converts a loaded profile to API strings.
     *
     * @param profile catalog profile
     * @param home SDLC home
     * @return view
     */
    public static ProjectView from(ProjectProfile profile, Path home) {
        return new ProjectView(
                profile.id(),
                relative(home, profile.seed()),
                relative(home, profile.repoPath()),
                profile.branchPrefix(),
                profile.sourceGlobs(),
                profile.conventions() == null ? "" : relative(home, profile.conventions()),
                profile.commands(),
                profile.timeout().toSeconds(),
                profile.javaHome() == null ? "" : profile.javaHome().toString());
    }

    private static String relative(Path home, Path path) {
        if (path == null) {
            return "";
        }
        Path absoluteHome = home.toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(absoluteHome)) {
            return absoluteHome.relativize(absolute).toString().replace('\\', '/');
        }
        return path.toString().replace('\\', '/');
    }
}
