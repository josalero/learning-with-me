package dev.mytechprofile.sdlc.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Body to copy a seed into {@code workspace/}.
 *
 * <p>Sample: {@code {"projectId":"users-service-java"}}.
 */
public record SeedRequest(@NotBlank String projectId) {}
