package dev.mytechprofile.tokenaudit.review;

/**
 * Indicates evidence collection, provider, or structured-response failure.
 */
public final class LlmReviewException extends RuntimeException {
	/**
	 * Creates a review failure.
	 *
	 * @param message failure description
	 */
	public LlmReviewException(String message) {
		super(message);
	}

	/**
	 * Creates a review failure with its cause.
	 *
	 * @param message failure description
	 * @param cause underlying failure
	 */
	public LlmReviewException(String message, Throwable cause) {
		super(message, cause);
	}
}
