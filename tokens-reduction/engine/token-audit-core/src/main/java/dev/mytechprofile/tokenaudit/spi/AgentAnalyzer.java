package dev.mytechprofile.tokenaudit.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;

/**
 * Scans single-agent and multi-agent orchestration for token waste.
 */
public interface AgentAnalyzer {
	/**
	 * Analyzes agent loops, delegation, handoffs, and shared context.
	 *
	 * @param projectPath project root
	 * @param frameworks target frameworks
	 * @return findings; empty when none or not implemented
	 */
	List<Finding> analyze(Path projectPath, Set<Framework> frameworks);
}
