package dev.mytechprofile.tokenaudit.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;

/**
 * Scans prompts and system messages for token waste.
 */
public interface PromptAnalyzer {
	/**
	 * Analyzes prompts under {@code projectPath}.
	 *
	 * @param projectPath project root
	 * @param frameworks target frameworks
	 * @return findings; empty when none or not implemented
	 */
	List<Finding> analyze(Path projectPath, Set<Framework> frameworks);
}
