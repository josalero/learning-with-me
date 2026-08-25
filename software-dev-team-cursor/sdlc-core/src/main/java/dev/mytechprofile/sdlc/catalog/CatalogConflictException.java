package dev.mytechprofile.sdlc.catalog;

/**
 * Thrown when creating a team or project whose id already exists.
 *
 * <p>Sample: {@code throw new CatalogConflictException("Team default-scrum-team already exists")}.
 */
public class CatalogConflictException extends CatalogException {

    /**
     * Creates a conflict error.
     *
     * @param message what already exists
     */
    public CatalogConflictException(String message) {
        super(message);
    }
}
