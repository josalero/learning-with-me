package dev.mytechprofile.tokenaudit.spi;

/**
 * Counts (or estimates) tokens for text.
 *
 * <p>The default implementation is
 * {@link dev.mytechprofile.tokenaudit.estimate.JtokkitTokenEstimator} ({@code cl100k_base}).
 * Tests may substitute a cheap heuristic via {@link NoOpAnalyzers#estimator()}.
 */
public interface TokenEstimator {
	/**
	 * Counts tokens for {@code text}.
	 *
	 * @param text input text; null treated as empty
	 * @return non-negative token count
	 */
	int estimate(String text);
}
