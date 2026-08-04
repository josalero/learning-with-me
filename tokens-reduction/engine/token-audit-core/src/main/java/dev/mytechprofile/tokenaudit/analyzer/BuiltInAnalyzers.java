package dev.mytechprofile.tokenaudit.analyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Severity;
import dev.mytechprofile.tokenaudit.estimate.JtokkitTokenEstimator;
import dev.mytechprofile.tokenaudit.spi.AgentAnalyzer;
import dev.mytechprofile.tokenaudit.spi.MemoryAnalyzer;
import dev.mytechprofile.tokenaudit.spi.PromptAnalyzer;
import dev.mytechprofile.tokenaudit.spi.RagAnalyzer;
import dev.mytechprofile.tokenaudit.spi.ToolAnalyzer;

/**
 * Conservative regex analyzers for common Java token-waste patterns.
 *
 * <p>These checks intentionally favor precise source shapes over broad guesses.
 * They are a useful baseline and demonstration, not a replacement for framework
 * AST analysis or runtime telemetry. The per-file methods ({@link #toolFindings},
 * {@link #ragFindings}) are also used by {@link AstAnalyzers} as a fallback when a
 * source file cannot be parsed into an AST.
 */
public final class BuiltInAnalyzers {

	static final int PROMPT_TOKEN_MIN_CHARS = 800;
	static final int TOOL_METHOD_THRESHOLD = 5;
	static final int TOP_K_THRESHOLD = 8;

	private static final JtokkitTokenEstimator TOKEN_ESTIMATOR = JtokkitTokenEstimator.cl100k();
	private static final String TOKEN_ENCODING_LABEL = TOKEN_ESTIMATOR.encodingName();

	/** Large text block assigned to a constant/field whose name reads like a system prompt. */
	private static final Pattern NAMED_SYSTEM_PROMPT = Pattern.compile(
			"(?is)\\b[A-Za-z_][A-Za-z0-9_]*(?:SYSTEM|PROMPT|INSTRUCTION)[A-Za-z0-9_]*"
					+ "\\s*=\\s*\"\"\"(.*?)\"\"\""
	);
	/** Large text block passed inline to a fluent {@code system(...)}/{@code defaultSystem(...)} call. */
	private static final Pattern FLUENT_SYSTEM_PROMPT = Pattern.compile(
			"(?is)\\.(?:defaultSystem|system)\\s*\\(\\s*\"\"\"(.*?)\"\"\""
	);
	/**
	 * A method-level tool declaration (Spring AI {@code @Tool}, LangChain4j {@code @Tool}).
	 * Anchored to the start of a source line so Javadoc mentions like {@code {@code @Tool}} are ignored.
	 */
	private static final Pattern TOOL_ANNOTATION = Pattern.compile("(?m)^\\s*@Tool\\b");
	/** A catch-all tool collection registered directly on a client. */
	private static final Pattern ALL_TOOLS = Pattern.compile(
			"(?i)\\.(?:defaultToolCallbacks|toolCallbacks|defaultTools|tools)"
					+ "\\s*\\(\\s*all_?tools\\s*\\)"
	);
	private static final Pattern TOP_K = Pattern.compile("\\.topK\\s*\\(\\s*(\\d+)\\s*\\)");
	private static final Pattern UNBOUNDED_HISTORY = Pattern.compile(
			"(?m)^\\s*(?:private\\s+)?(?:final\\s+)?List<[^>]+>\\s+"
					+ "(?:conversationHistory|chatHistory|conversation|history|transcript|messages|memory)"
					+ "\\s*=\\s*new\\s+ArrayList"
	);
	private static final Pattern FULL_CONTEXT_HANDOFF = Pattern.compile(
			"(?i)\\.(?:delegate|handoff|escalate|dispatch|forward)\\s*\\(\\s*"
					+ "[A-Za-z_][A-Za-z0-9_]*(?:context|history|conversation|transcript|messages|bundle)"
					+ "[A-Za-z0-9_]*\\s*\\)"
	);

	private BuiltInAnalyzers() {
	}

	/**
	 * Returns the built-in prompt analyzer.
	 *
	 * @return prompt analyzer
	 */
	public static PromptAnalyzer prompt() {
		return (projectPath, frameworks) -> forEachSource(projectPath, BuiltInAnalyzers::promptFindings);
	}

	/**
	 * Returns the regex-based tool analyzer.
	 *
	 * @return tool analyzer
	 */
	public static ToolAnalyzer tool() {
		return (projectPath, frameworks) -> forEachSource(projectPath, BuiltInAnalyzers::toolFindings);
	}

	/**
	 * Returns the regex-based retrieval analyzer.
	 *
	 * @return retrieval analyzer
	 */
	public static RagAnalyzer rag() {
		return (projectPath, frameworks) -> forEachSource(projectPath, BuiltInAnalyzers::ragFindings);
	}

	/**
	 * Returns the built-in memory analyzer.
	 *
	 * @return memory analyzer
	 */
	public static MemoryAnalyzer memory() {
		return (projectPath, frameworks) -> forEachSource(projectPath, BuiltInAnalyzers::memoryFindings);
	}

	/**
	 * Returns the built-in agent analyzer.
	 *
	 * @return agent analyzer
	 */
	public static AgentAnalyzer agent() {
		return (projectPath, frameworks) -> forEachSource(projectPath, BuiltInAnalyzers::agentFindings);
	}

	private static List<Finding> forEachSource(
			Path projectPath,
			java.util.function.Function<SourceFile, List<Finding>> perFile
	) {
		List<Finding> findings = new ArrayList<>();
		for (SourceFile source : SourceScanner.javaSources(projectPath)) {
			findings.addAll(perFile.apply(source));
		}
		return findings;
	}

	static List<Finding> promptFindings(SourceFile source) {
		List<Finding> findings = new ArrayList<>();
		addPromptFindings(findings, source, NAMED_SYSTEM_PROMPT);
		addPromptFindings(findings, source, FLUENT_SYSTEM_PROMPT);
		return findings;
	}

	private static void addPromptFindings(List<Finding> findings, SourceFile source, Pattern pattern) {
		Matcher matcher = pattern.matcher(source.text());
		while (matcher.find()) {
			String prompt = matcher.group(1);
			if (prompt.length() >= PROMPT_TOKEN_MIN_CHARS) {
				findings.add(Findings.atOffset(
						"PROMPT-OVERSIZED-SYSTEM",
						Severity.MEDIUM,
						"prompts",
						source,
						matcher.start(),
						"A large system prompt text block is sent on every request (~"
								+ estimateTokens(prompt) + " tokens, " + TOKEN_ENCODING_LABEL + ").",
						"Shorten the stable instructions and move task-specific details to on-demand context.",
						estimateTokens(prompt)
				));
			}
		}
	}

	/**
	 * Regex-based tool findings for a single source file. Also serves as the AST fallback.
	 *
	 * @param source source file
	 * @return tool findings
	 */
	static List<Finding> toolFindings(SourceFile source) {
		List<Finding> findings = new ArrayList<>();
		Matcher toolMatcher = TOOL_ANNOTATION.matcher(source.text());
		int toolCount = 0;
		int firstOffset = -1;
		while (toolMatcher.find()) {
			if (firstOffset < 0) {
				firstOffset = toolMatcher.start();
			}
			toolCount++;
		}
		if (toolCount >= TOOL_METHOD_THRESHOLD) {
			findings.add(Findings.atOffset(
					"TOOLS-ALL-REGISTERED",
					Severity.HIGH,
					"tools",
					source,
					firstOffset,
					"This class exposes " + toolCount + " model-callable @Tool methods that are attached "
							+ "together; each enlarges the tool schema and may expose privileged actions.",
					"Group tools by intent, attach only what a classified request needs, and keep "
							+ "privileged tools off general user paths.",
					null
			));
		}

		Matcher allMatcher = ALL_TOOLS.matcher(source.text());
		while (allMatcher.find()) {
			findings.add(Findings.atOffset(
					"TOOLS-ALL-REGISTERED",
					Severity.HIGH,
					"tools",
					source,
					allMatcher.start(),
					"A catch-all tool collection is attached to this request path.",
					"Select a small tool set from the request intent and keep admin tools off user paths.",
					null
			));
		}
		return findings;
	}

	/**
	 * Regex-based retrieval findings for a single source file. Also serves as the AST fallback.
	 *
	 * @param source source file
	 * @return retrieval findings
	 */
	static List<Finding> ragFindings(SourceFile source) {
		List<Finding> findings = new ArrayList<>();
		Matcher matcher = TOP_K.matcher(source.text());
		while (matcher.find()) {
			int topK = Integer.parseInt(matcher.group(1));
			if (topK > TOP_K_THRESHOLD) {
				findings.add(Findings.atOffset(
						"RAG-EXCESSIVE-TOP-K",
						Severity.MEDIUM,
						"rag",
						source,
						matcher.start(),
						"Retrieval topK=" + topK + " can inject many low-value chunks.",
						"Start with topK=4 and a similarity threshold, then tune with retrieval evaluation.",
						null
				));
			}
		}
		return findings;
	}

	private static List<Finding> memoryFindings(SourceFile source) {
		return regexFindings(
				source,
				UNBOUNDED_HISTORY,
				"MEMORY-UNBOUNDED-HISTORY",
				Severity.HIGH,
				"memory",
				"Conversation history uses an append-only ArrayList with no token or message window.",
				"Use bounded message/token memory and summarize before eviction."
		);
	}

	private static List<Finding> agentFindings(SourceFile source) {
		return regexFindings(
				source,
				FULL_CONTEXT_HANDOFF,
				"AGENT-OVERSIZED-HANDOFF",
				Severity.HIGH,
				"agents",
				"A child agent receives the complete shared context.",
				"Build a task-scoped handoff containing only the goal, constraints, and relevant evidence."
		);
	}

	private static List<Finding> regexFindings(
			SourceFile source,
			Pattern pattern,
			String id,
			Severity severity,
			String area,
			String message,
			String recommendation
	) {
		List<Finding> findings = new ArrayList<>();
		Matcher matcher = pattern.matcher(source.text());
		while (matcher.find()) {
			findings.add(Findings.atOffset(id, severity, area, source, matcher.start(), message,
					recommendation, null));
		}
		return findings;
	}

	private static int estimateTokens(String text) {
		return TOKEN_ESTIMATOR.estimate(text);
	}
}
