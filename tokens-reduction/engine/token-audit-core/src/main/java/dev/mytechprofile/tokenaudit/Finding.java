package dev.mytechprofile.tokenaudit;

import java.util.Objects;

/**
 * One token-efficiency finding produced by an analyzer.
 *
 * @param id stable rule identifier for deterministic findings, normalized response identifier for AI findings
 * @param severity severity
 * @param area area such as prompts, tools, rag, memory, agents
 * @param location file or symbol location
 * @param message human-readable issue description
 * @param recommendation suggested remediation
 * @param estimatedTokens optional token count (for example jtokkit {@code cl100k_base}); may be null
 * @param origin how the finding was produced
 */
public record Finding(
		String id,
		Severity severity,
		String area,
		String location,
		String message,
		String recommendation,
		Integer estimatedTokens,
		FindingOrigin origin
) {
	/** Validates required finding fields and estimates. */
	public Finding {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(area, "area");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(recommendation, "recommendation");
		Objects.requireNonNull(origin, "origin");
		if (estimatedTokens != null && estimatedTokens < 0) {
			throw new IllegalArgumentException("estimatedTokens must not be negative");
		}
	}

	/**
	 * Backward-compatible constructor for deterministic analyzers.
	 *
	 * @param id stable rule identifier
	 * @param severity finding severity
	 * @param area audit area
	 * @param location source location
	 * @param message issue description
	 * @param recommendation suggested remediation
	 * @param estimatedTokens optional heuristic estimate
	 */
	public Finding(
			String id,
			Severity severity,
			String area,
			String location,
			String message,
			String recommendation,
			Integer estimatedTokens
	) {
		this(
				id,
				severity,
				area,
				location,
				message,
				recommendation,
				estimatedTokens,
				FindingOrigin.DETERMINISTIC
		);
	}
}
