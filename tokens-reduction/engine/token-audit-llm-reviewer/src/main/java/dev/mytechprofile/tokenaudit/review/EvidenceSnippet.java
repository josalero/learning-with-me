package dev.mytechprofile.tokenaudit.review;

import java.util.Objects;

/**
 * One redacted, line-numbered source excerpt.
 *
 * @param relativePath source path relative to the audited project
 * @param content bounded and redacted source content
 */
public record EvidenceSnippet(String relativePath, String content) {
	/** Validates one evidence snippet. */
	public EvidenceSnippet {
		Objects.requireNonNull(relativePath, "relativePath");
		Objects.requireNonNull(content, "content");
	}
}
