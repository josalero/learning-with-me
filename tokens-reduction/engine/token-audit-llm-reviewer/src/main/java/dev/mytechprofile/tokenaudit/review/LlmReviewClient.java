package dev.mytechprofile.tokenaudit.review;

/**
 * Provider-neutral client for one structured semantic-review completion.
 */
public interface LlmReviewClient {
	/**
	 * Requests a structured review from the configured model provider.
	 *
	 * @param request bounded prompt and output schema
	 * @return provider response and usage metadata
	 */
	LlmReviewResponse complete(LlmReviewRequest request);
}
