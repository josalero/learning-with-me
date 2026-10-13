package dev.mytechprofile.langchain122.screening.openrouter;

/**
 * OpenRouter rejected a decisions call, or the response could not be mapped onto a LangChain4j answer.
 *
 * <p>The message names the HTTP status or the missing field. It does not include the API key, the
 * recruiter note, or the response body.
 */
public final class OpenRouterDecisionException extends RuntimeException {

    /**
     * @param message what failed and what to check, with no request or response payload
     */
    public OpenRouterDecisionException(String message) {
        super(message);
    }

    /**
     * @param message what failed and what to check, with no request or response payload
     * @param cause the timeout, I/O failure, or interrupt
     */
    public OpenRouterDecisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
