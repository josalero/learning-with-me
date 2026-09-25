package dev.mytechprofile.jev.decide;

/**
 * OpenRouter Decisions call failed or returned a label outside the rubric.
 *
 * <p>The API maps this to HTTP 502 with a generic message. The detail here is for
 * logs and tests; it must not contain the resume or the upstream body.
 *
 * @param message status or parse failure, for example {@code OpenRouter decisions HTTP 401}
 */
public class JevClientException extends RuntimeException {

    /**
     * @param message safe detail; do not pass the resume or the raw response body
     */
    public JevClientException(String message) {
        super(message);
    }

    /**
     * Keeps the cause for server logs. The message shown to clients stays generic.
     *
     * @param message safe detail; do not pass the resume or the raw response body
     * @param cause underlying HTTP, timeout, or parse failure
     */
    public JevClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
