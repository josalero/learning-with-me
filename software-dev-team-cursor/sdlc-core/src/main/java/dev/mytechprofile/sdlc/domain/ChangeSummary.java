package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * Developer output after writing files.
 *
 * <p>Sample: {@code new ChangeSummary(List.of("UserController.java"), "Add 404 problem
 * details", "")}.
 */
public record ChangeSummary(List<String> filesTouched, String rationale, String notes) {

    public ChangeSummary {
        filesTouched = filesTouched == null ? List.of() : List.copyOf(filesTouched);
        rationale = Objects.requireNonNullElse(rationale, "").trim();
        notes = Objects.requireNonNullElse(notes, "").trim();
    }

    public static ChangeSummary none() {
        return new ChangeSummary(List.of(), "", "");
    }
}
