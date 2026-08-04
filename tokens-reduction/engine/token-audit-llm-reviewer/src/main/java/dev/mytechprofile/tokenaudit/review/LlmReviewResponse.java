package dev.mytechprofile.tokenaudit.review;

import java.util.Objects;

/**
 * Raw structured completion and provider usage metadata.
 *
 * @param content JSON content returned by the model
 * @param model concrete model reported by the provider
 * @param promptTokens provider-reported input tokens, or {@code null}
 * @param completionTokens provider-reported output tokens, or {@code null}
 */
public record LlmReviewResponse(
		String content,
		String model,
		Integer promptTokens,
		Integer completionTokens
) {
	/** Validates provider content and usage counts. */
	public LlmReviewResponse {
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(model, "model");
		if (promptTokens != null && promptTokens < 0) {
			throw new IllegalArgumentException("promptTokens must not be negative");
		}
		if (completionTokens != null && completionTokens < 0) {
			throw new IllegalArgumentException("completionTokens must not be negative");
		}
	}
}
