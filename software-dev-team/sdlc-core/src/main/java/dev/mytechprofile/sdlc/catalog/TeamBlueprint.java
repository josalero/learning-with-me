package dev.mytechprofile.sdlc.catalog;

import dev.mytechprofile.sdlc.domain.RoleKind;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Who is on the team and how loops are bounded.
 *
 * <p>Sample: {@code default-scrum-team} with six roles and the default policy.
 */
public record TeamBlueprint(String id, List<RoleSpec> roles, TeamPolicy policy) {

    public TeamBlueprint {
        id = Objects.requireNonNull(id, "team id is required").trim();
        roles = roles == null ? List.of() : List.copyOf(roles);
        policy = Objects.requireNonNull(policy, "team policy is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("team id must be non-blank");
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("team " + id + " must declare at least one role");
        }
    }

    public Optional<RoleSpec> role(RoleKind kind) {
        return roles.stream().filter(role -> role.kind() == kind).findFirst();
    }

    public boolean has(RoleKind kind) {
        return role(kind).isPresent();
    }
}
