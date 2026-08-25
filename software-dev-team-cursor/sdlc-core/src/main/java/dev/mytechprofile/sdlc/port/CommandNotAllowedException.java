package dev.mytechprofile.sdlc.port;

/**
 * Thrown when a command is not in the project allowlist.
 */
public class CommandNotAllowedException extends RuntimeException {

    public CommandNotAllowedException(String message) {
        super(message);
    }
}
