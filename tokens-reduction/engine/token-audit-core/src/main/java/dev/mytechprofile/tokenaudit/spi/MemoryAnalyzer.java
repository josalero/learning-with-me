package dev.mytechprofile.tokenaudit.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;

/**
 * Scans chat-memory configuration for unbounded or wasteful history.
 */
public interface MemoryAnalyzer {
	/**
	 * Analyzes memory usage under {@code projectPath}.
	 *
	 * @param projectPath project root
	 * @param frameworks target frameworks
	 * @return findings; empty when none or not implemented
	 */
	List<Finding> analyze(Path projectPath, Set<Framework> frameworks);
}
