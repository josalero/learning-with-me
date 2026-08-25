package dev.mytechprofile.sdlc.port;

/**
 * Thrown when a path escapes the repository jail.
 */
public class PathJailException extends RuntimeException {

    public PathJailException(String message) {
        super(message);
    }
}
