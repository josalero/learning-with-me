package dev.mytechprofile.sdlc.api;

import dev.mytechprofile.sdlc.domain.ArtifactFile;

/**
 * Catalog row for a numbered run artifact.
 *
 * <p>Sample: {@code 01-feature-brief.json} answers "What are we building?".
 */
public record ArtifactFileView(String fileName, String stateKey, String question) {

    /**
     * Maps an enum constant to the API row.
     *
     * @param file artifact enum
     * @return view
     */
    public static ArtifactFileView from(ArtifactFile file) {
        return new ArtifactFileView(file.fileName(), file.stateKey(), file.question());
    }
}
