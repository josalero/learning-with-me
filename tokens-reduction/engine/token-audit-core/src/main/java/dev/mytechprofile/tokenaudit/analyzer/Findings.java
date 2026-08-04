package dev.mytechprofile.tokenaudit.analyzer;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Severity;

/**
 * Small factory for building {@link Finding} instances with a {@code path:line} location.
 */
final class Findings {

	private Findings() {
	}

	/**
	 * Builds a finding at an explicit 1-based line (preferred; AST nodes carry line numbers).
	 *
	 * @param id stable finding id
	 * @param severity severity
	 * @param area audit area
	 * @param source the source file
	 * @param line 1-based line number
	 * @param message issue description
	 * @param recommendation remediation
	 * @param estimatedTokens optional token estimate, may be null
	 * @return the finding
	 */
	static Finding atLine(
			String id,
			Severity severity,
			String area,
			SourceFile source,
			int line,
			String message,
			String recommendation,
			Integer estimatedTokens
	) {
		return new Finding(
				id,
				severity,
				area,
				source.relativePath() + ":" + line,
				message,
				recommendation,
				estimatedTokens
		);
	}

	/**
	 * Builds a finding from a character offset (used by the regex path, which lacks line numbers).
	 *
	 * @param id stable finding id
	 * @param severity severity
	 * @param area audit area
	 * @param source the source file
	 * @param offset 0-based character offset of the match
	 * @param message issue description
	 * @param recommendation remediation
	 * @param estimatedTokens optional token estimate, may be null
	 * @return the finding
	 */
	static Finding atOffset(
			String id,
			Severity severity,
			String area,
			SourceFile source,
			int offset,
			String message,
			String recommendation,
			Integer estimatedTokens
	) {
		return atLine(id, severity, area, source, lineOf(source.text(), offset), message,
				recommendation, estimatedTokens);
	}

	private static int lineOf(String text, int offset) {
		int line = 1;
		int limit = Math.min(offset, text.length());
		for (int i = 0; i < limit; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}
}
