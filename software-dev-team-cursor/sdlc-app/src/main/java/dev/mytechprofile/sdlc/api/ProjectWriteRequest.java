package dev.mytechprofile.sdlc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Body for creating or updating a project YAML file.
 *
 * <p>Sample: {@code seed: seeds/users-service-java} with {@code test} argv.
 */
public record ProjectWriteRequest(
        @NotBlank String id,
        @NotBlank String seed,
        @NotBlank String repoPath,
        String branchPrefix,
        List<String> sourceGlobs,
        String conventions,
        @NotEmpty Map<String, List<String>> commands,
        Long timeoutSeconds,
        String javaHome) {}
