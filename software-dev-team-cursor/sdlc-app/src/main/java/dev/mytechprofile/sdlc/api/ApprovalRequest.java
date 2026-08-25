package dev.mytechprofile.sdlc.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Body for a human stakeholder decision.
 *
 * <p>Sample: {@code {"decision":"APPROVED","reasons":["Matches the brief"],"followUps":[]}}.
 */
public record ApprovalRequest(@NotBlank String decision, List<String> reasons, List<String> followUps) {}
