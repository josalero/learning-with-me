package dev.mytechprofile.tokenaudit.review;

import java.util.List;
import java.util.Objects;

/**
 * Bounded evidence selected for external semantic review.
 *
 * @param snippets selected source excerpts
 * @param redactionCount number of potential secret values replaced
 * @param truncated whether eligible evidence was omitted or shortened
 * @param characterCount characters in the final evidence content
 */
public record EvidenceBundle(
		List<EvidenceSnippet> snippets,
		int redactionCount,
		boolean truncated,
		int characterCount
) {
	/** Validates and copies the evidence bundle. */
	public EvidenceBundle {
		snippets = List.copyOf(Objects.requireNonNull(snippets, "snippets"));
		if (redactionCount < 0 || characterCount < 0) {
			throw new IllegalArgumentException("evidence counts must not be negative");
		}
	}
}
