package dev.mytechprofile.tokenaudit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.mytechprofile.tokenaudit.analyzer.AstAnalyzers;
import dev.mytechprofile.tokenaudit.analyzer.BuiltInAnalyzers;
import dev.mytechprofile.tokenaudit.estimate.JtokkitTokenEstimator;
import dev.mytechprofile.tokenaudit.spi.AgentAnalyzer;
import dev.mytechprofile.tokenaudit.spi.MemoryAnalyzer;
import dev.mytechprofile.tokenaudit.spi.PromptAnalyzer;
import dev.mytechprofile.tokenaudit.spi.RagAnalyzer;
import dev.mytechprofile.tokenaudit.spi.TokenEstimator;
import dev.mytechprofile.tokenaudit.spi.ToolAnalyzer;

/**
 * Entry point for static token-efficiency analysis of a project tree.
 *
 * <p>Sample:
 * <pre>{@code
 * TokenAuditResult result = TokenAuditor.builder()
 *         .projectPath(Path.of("."))
 *         .frameworks(Framework.SPRING_AI, Framework.LANGCHAIN4J)
 *         .analyze();
 * result.findings().forEach(System.out::println);
 * }</pre>
 */
public final class TokenAuditor {

	private final Path projectPath;
	private final Set<Framework> frameworks;
	private final PromptAnalyzer promptAnalyzer;
	private final ToolAnalyzer toolAnalyzer;
	private final RagAnalyzer ragAnalyzer;
	private final MemoryAnalyzer memoryAnalyzer;
	private final AgentAnalyzer agentAnalyzer;
	private final TokenEstimator tokenEstimator;

	private TokenAuditor(Builder builder) {
		this.projectPath = builder.projectPath;
		this.frameworks = Set.copyOf(builder.frameworks);
		this.promptAnalyzer = builder.promptAnalyzer;
		this.toolAnalyzer = builder.toolAnalyzer;
		this.ragAnalyzer = builder.ragAnalyzer;
		this.memoryAnalyzer = builder.memoryAnalyzer;
		this.agentAnalyzer = builder.agentAnalyzer;
		this.tokenEstimator = builder.tokenEstimator;
	}

	/**
	 * Creates a new builder.
	 *
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Runs configured analyzers and returns aggregated findings.
	 *
	 * @return audit result
	 * @throws IllegalStateException if the project path is missing or not a directory
	 */
	public TokenAuditResult analyze() {
		if (projectPath == null) {
			throw new IllegalStateException("projectPath is required");
		}
		if (!Files.isDirectory(projectPath)) {
			throw new IllegalStateException("projectPath must be an existing directory: " + projectPath);
		}
		if (frameworks.isEmpty()) {
			throw new IllegalStateException("at least one framework is required");
		}

		List<Finding> findings = new ArrayList<>();
		findings.addAll(promptAnalyzer.analyze(projectPath, frameworks));
		findings.addAll(toolAnalyzer.analyze(projectPath, frameworks));
		findings.addAll(ragAnalyzer.analyze(projectPath, frameworks));
		findings.addAll(memoryAnalyzer.analyze(projectPath, frameworks));
		findings.addAll(agentAnalyzer.analyze(projectPath, frameworks));
		return new TokenAuditResult(projectPath.toAbsolutePath().normalize(), frameworks, findings);
	}

	/**
	 * Fluent builder for {@link TokenAuditor}.
	 */
	public static final class Builder {
		private Path projectPath;
		private final EnumSet<Framework> frameworks = EnumSet.noneOf(Framework.class);
		private PromptAnalyzer promptAnalyzer = BuiltInAnalyzers.prompt();
		private ToolAnalyzer toolAnalyzer = AstAnalyzers.tool();
		private RagAnalyzer ragAnalyzer = AstAnalyzers.rag();
		private MemoryAnalyzer memoryAnalyzer = BuiltInAnalyzers.memory();
		private AgentAnalyzer agentAnalyzer = BuiltInAnalyzers.agent();
		private TokenEstimator tokenEstimator = JtokkitTokenEstimator.cl100k();

		private Builder() {
		}

		/**
		 * Sets the project root to scan.
		 *
		 * @param projectPath directory path
		 * @return this builder
		 */
		public Builder projectPath(Path projectPath) {
			this.projectPath = Objects.requireNonNull(projectPath, "projectPath");
			return this;
		}

		/**
		 * Adds frameworks to analyze.
		 *
		 * @param first first framework
		 * @param rest additional frameworks
		 * @return this builder
		 */
		public Builder frameworks(Framework first, Framework... rest) {
			Objects.requireNonNull(first, "first");
			frameworks.add(first);
			if (rest != null) {
				for (Framework framework : rest) {
					frameworks.add(Objects.requireNonNull(framework, "framework"));
				}
			}
			return this;
		}

		/**
		 * Replaces analyzers (for tests or custom pipelines).
		 *
		 * @param promptAnalyzer prompt analyzer
		 * @return this builder
		 */
		public Builder promptAnalyzer(PromptAnalyzer promptAnalyzer) {
			this.promptAnalyzer = Objects.requireNonNull(promptAnalyzer, "promptAnalyzer");
			return this;
		}

		public Builder toolAnalyzer(ToolAnalyzer toolAnalyzer) {
			this.toolAnalyzer = Objects.requireNonNull(toolAnalyzer, "toolAnalyzer");
			return this;
		}

		public Builder ragAnalyzer(RagAnalyzer ragAnalyzer) {
			this.ragAnalyzer = Objects.requireNonNull(ragAnalyzer, "ragAnalyzer");
			return this;
		}

		public Builder memoryAnalyzer(MemoryAnalyzer memoryAnalyzer) {
			this.memoryAnalyzer = Objects.requireNonNull(memoryAnalyzer, "memoryAnalyzer");
			return this;
		}

		/**
		 * Replaces the analyzer for agent loops, delegation, and handoffs.
		 *
		 * @param agentAnalyzer agent analyzer
		 * @return this builder
		 */
		public Builder agentAnalyzer(AgentAnalyzer agentAnalyzer) {
			this.agentAnalyzer = Objects.requireNonNull(agentAnalyzer, "agentAnalyzer");
			return this;
		}

		public Builder tokenEstimator(TokenEstimator tokenEstimator) {
			this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
			return this;
		}

		/**
		 * Builds and runs the auditor.
		 *
		 * @return audit result
		 */
		public TokenAuditResult analyze() {
			return build().analyze();
		}

		/**
		 * Builds the auditor without running it.
		 *
		 * @return auditor
		 */
		public TokenAuditor build() {
			return new TokenAuditor(this);
		}
	}
}
