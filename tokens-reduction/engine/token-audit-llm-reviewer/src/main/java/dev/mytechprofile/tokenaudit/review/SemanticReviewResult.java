package dev.mytechprofile.tokenaudit.review;

import java.util.List;
import java.util.Objects;

import dev.mytechprofile.tokenaudit.Finding;

/**
 * Parsed AI-inferred findings and review metadata.
 *
 * @param findings validated AI-inferred findings
 * @param model concrete model reported by the provider
 * @param promptTokens provider-reported input tokens, or {@code null}
 * @param completionTokens provider-reported output tokens, or {@code null}
 * @param evidenceFiles files included in the request
 * @param redactionCount potential secret values removed
 * @param evidenceTruncated whether evidence was omitted or shortened
 */
public record SemanticReviewResult(
		List<Finding> findings,
		String model,
		Integer promptTokens,
		Integer completionTokens,
		int evidenceFiles,
		int redactionCount,
		boolean evidenceTruncated
) {
	/** Validates and copies semantic-review results. */
	public SemanticReviewResult {
		findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
		Objects.requireNonNull(model, "model");
	}
}
