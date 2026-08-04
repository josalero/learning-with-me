package dev.mytechprofile.tokenaudit.review;

import java.nio.file.Path;

/**
 * Selects and sanitizes source evidence before it leaves the local process.
 */
public interface EvidenceCollector {
	/**
	 * Collects a bounded evidence bundle.
	 *
	 * @param projectPath audited project root
	 * @return redacted evidence
	 */
	EvidenceBundle collect(Path projectPath);
}
