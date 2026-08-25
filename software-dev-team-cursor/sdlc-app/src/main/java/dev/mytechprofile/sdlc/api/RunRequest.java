package dev.mytechprofile.sdlc.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Body to start a feature run.
 *
 * <p>Sample: {@code new RunRequest("default-scrum-team", "users-service-java", "Return 404...")}.
 */
public record RunRequest(@NotBlank String teamId, @NotBlank String projectId, @NotBlank String featureRequest) {}
