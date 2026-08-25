package dev.mytechprofile.sdlc.catalog;

import dev.mytechprofile.sdlc.domain.RoleKind;
import java.util.Objects;

/**
 * One role on a team blueprint.
 *
 * <p>Sample: Developer using {@code MODEL_STRONG} at temperature 0.0.
 */
public record RoleSpec(String id, RoleKind kind, String model, String prompt, double temperature) {

    public RoleSpec {
        id = Objects.requireNonNull(id, "role id is required").trim();
        kind = Objects.requireNonNull(kind, "role kind is required");
        model = Objects.requireNonNull(model, "role model is required").trim();
        prompt = Objects.requireNonNull(prompt, "role prompt is required").trim();
        if (id.isBlank() || model.isBlank() || prompt.isBlank()) {
            throw new IllegalArgumentException("role id, model, and prompt must be non-blank");
        }
    }
}
