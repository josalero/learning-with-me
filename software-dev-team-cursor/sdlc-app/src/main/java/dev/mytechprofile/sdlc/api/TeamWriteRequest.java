package dev.mytechprofile.sdlc.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body for creating or updating a team YAML file.
 *
 * <p>Sample: {@code new TeamWriteRequest("lean-pair", roles, policy)}.
 */
public record TeamWriteRequest(
        @NotBlank String id, @NotEmpty List<RoleWriteRequest> roles, @NotNull PolicyWriteRequest policy) {

    /**
     * One role row in a team document.
     *
     * <p>Sample: developer with {@code ${MODEL_STRONG}}.
     */
    public record RoleWriteRequest(
            @NotBlank String id,
            @NotBlank String kind,
            @NotBlank String model,
            @NotBlank String prompt,
            Double temperature) {}

    /**
     * Loop caps written to team YAML.
     *
     * <p>Sample: {@code stakeholderMode: HUMAN} with default maxima.
     */
    public record PolicyWriteRequest(
            Integer maxSpecRework,
            Integer maxImplementationAttempts,
            Integer maxReviewCycles,
            Integer maxQaCycles,
            Integer maxStakeholderCycles,
            Integer qaPassThreshold,
            String stakeholderMode,
            Integer maxDeveloperToolCalls,
            Integer maxReadOnlyToolCalls) {}
}
