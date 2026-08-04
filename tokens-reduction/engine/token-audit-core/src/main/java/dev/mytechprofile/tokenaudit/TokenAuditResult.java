package dev.mytechprofile.tokenaudit;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Outcome of a project token audit.
 *
 * @param projectPath scanned root
 * @param frameworks frameworks requested for analysis
 * @param findings findings in discovery order
 */
public record TokenAuditResult(
		Path projectPath,
		Set<Framework> frameworks,
		List<Finding> findings
) {
	public TokenAuditResult {
		Objects.requireNonNull(projectPath, "projectPath");
		frameworks = Set.copyOf(Objects.requireNonNull(frameworks, "frameworks"));
		findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
	}

	/**
	 * Returns findings for iteration (for example printing).
	 *
	 * @return immutable findings list
	 */
	public List<Finding> findings() {
		return findings;
	}
}
