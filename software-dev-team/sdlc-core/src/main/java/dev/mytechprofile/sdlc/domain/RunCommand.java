package dev.mytechprofile.sdlc.domain;

import java.util.Objects;

/**
 * Command to start a feature run.
 *
 * <p>Sample: {@code new RunCommand("default-scrum-team", "users-service-java", "Return 404...")}.
 */
public record RunCommand(String teamId, String projectId, String featureRequest) {

    public RunCommand {
        teamId = Objects.requireNonNull(teamId, "teamId is required").trim();
        projectId = Objects.requireNonNull(projectId, "projectId is required").trim();
        featureRequest = Objects.requireNonNull(featureRequest, "featureRequest is required")
                .trim();
        if (teamId.isBlank() || projectId.isBlank() || featureRequest.isBlank()) {
            throw new IllegalArgumentException("teamId, projectId, and featureRequest are required");
        }
    }
}
