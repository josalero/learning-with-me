package dev.mytechprofile.tokenaudit.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;

/**
 * Opt-out analyzers for tests and custom pipelines that return no findings.
 */
public final class NoOpAnalyzers {

	private NoOpAnalyzers() {
	}

	public static PromptAnalyzer prompt() {
		return (Path projectPath, Set<Framework> frameworks) -> List.of();
	}

	public static ToolAnalyzer tool() {
		return (Path projectPath, Set<Framework> frameworks) -> List.of();
	}

	public static RagAnalyzer rag() {
		return (Path projectPath, Set<Framework> frameworks) -> List.of();
	}

	public static MemoryAnalyzer memory() {
		return (Path projectPath, Set<Framework> frameworks) -> List.of();
	}

	/**
	 * Returns an agent analyzer that produces no findings.
	 *
	 * @return no-op agent analyzer
	 */
	public static AgentAnalyzer agent() {
		return (Path projectPath, Set<Framework> frameworks) -> List.of();
	}

	/**
	 * Cheap character-length heuristic for tests. Prefer
	 * {@link dev.mytechprofile.tokenaudit.estimate.JtokkitTokenEstimator} in production.
	 *
	 * @return length/4 estimator
	 */
	public static TokenEstimator estimator() {
		return text -> {
			if (text == null || text.isEmpty()) {
				return 0;
			}
			return Math.max(1, (text.length() + 3) / 4);
		};
	}
}
