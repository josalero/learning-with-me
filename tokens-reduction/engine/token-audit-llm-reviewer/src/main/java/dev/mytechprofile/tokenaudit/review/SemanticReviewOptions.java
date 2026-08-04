package dev.mytechprofile.tokenaudit.review;

/**
 * Evidence and output limits for one semantic review.
 *
 * @param maxFiles maximum source files sent
 * @param maxCharactersPerFile maximum characters sent from one file
 * @param maxTotalCharacters maximum source characters sent in total
 * @param maxFindings maximum model-inferred findings accepted
 */
public record SemanticReviewOptions(
		int maxFiles,
		int maxCharactersPerFile,
		int maxTotalCharacters,
		int maxFindings
) {
	/** Validates evidence and output limits. */
	public SemanticReviewOptions {
		if (maxFiles <= 0 || maxCharactersPerFile <= 0
				|| maxTotalCharacters <= 0 || maxFindings <= 0) {
			throw new IllegalArgumentException("semantic review limits must be positive");
		}
		if (maxCharactersPerFile > maxTotalCharacters) {
			throw new IllegalArgumentException(
					"maxCharactersPerFile must not exceed maxTotalCharacters"
			);
		}
	}

	/**
	 * Conservative defaults suitable for an opt-in CLI review.
	 *
	 * @return default options
	 */
	public static SemanticReviewOptions defaults() {
		return new SemanticReviewOptions(20, 8_000, 40_000, 8);
	}
}
