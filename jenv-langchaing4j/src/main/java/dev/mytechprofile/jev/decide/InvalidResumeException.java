package dev.mytechprofile.jev.decide;

/**
 * The caller sent a resume the lab will not classify.
 *
 * <p>The API maps only this exception to HTTP 400 and returns {@code message}
 * as the body. Do not put file contents in the message.
 *
 * @param message reason the client can fix, for example {@code Resume text is empty}
 */
public class InvalidResumeException extends RuntimeException {

    /**
     * @param message client-facing reason; do not include file contents
     */
    public InvalidResumeException(String message) {
        super(message);
    }
}
