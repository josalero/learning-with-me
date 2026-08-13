package dev.mytechprofile.sdlc.port;

import dev.mytechprofile.sdlc.catalog.ProjectProfile;
import dev.mytechprofile.sdlc.domain.CommandResult;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs an allowlisted argv command in a project working directory.
 *
 * <p>Sample: {@code runner.run(profile, repo, "test")} executes the configured test argv.
 */
public interface CommandRunner {

    CommandResult run(ProjectProfile profile, Path workingDirectory, String commandName);

    CommandResult run(ProjectProfile profile, Path workingDirectory, List<String> argv);
}
