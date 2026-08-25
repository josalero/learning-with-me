package dev.mytechprofile.sdlc.catalog;

/**
 * Thrown when a team or project blueprint cannot be loaded.
 */
public class CatalogException extends RuntimeException {

    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
