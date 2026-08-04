package dev.mytechprofile.tokenaudit.analyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Severity;
import dev.mytechprofile.tokenaudit.spi.RagAnalyzer;
import dev.mytechprofile.tokenaudit.spi.ToolAnalyzer;

/**
 * AST-based analyzers built on JavaParser. These understand Java structure instead of
 * raw text, so they attach findings to exact declarations and ignore matches inside
 * comments, Javadoc, and string literals.
 *
 * <p>Each analyzer parses a file into a {@link CompilationUnit}. When a file cannot be
 * parsed (for example, it uses a newer language construct than the configured level),
 * the analyzer falls back to the regex implementation in {@link BuiltInAnalyzers} for
 * that file so scanning never silently drops coverage.
 */
public final class AstAnalyzers {

	private static final ParserConfiguration.LanguageLevel LANGUAGE_LEVEL =
			ParserConfiguration.LanguageLevel.JAVA_21;

	private AstAnalyzers() {
	}

	/**
	 * Returns an AST-based tool analyzer.
	 *
	 * <p>Counts methods annotated with {@code @Tool} per compilation unit and flags a
	 * class that attaches many model-callable tools together, plus catch-all tool
	 * registrations such as {@code .tools(allTools)}.
	 *
	 * @return tool analyzer
	 */
	public static ToolAnalyzer tool() {
		return (projectPath, frameworks) -> analyze(projectPath, AstAnalyzers::toolFindings,
				BuiltInAnalyzers::toolFindings);
	}

	/**
	 * Returns an AST-based retrieval analyzer.
	 *
	 * <p>Flags {@code topK(n)} calls with an integer-literal argument above the safe
	 * threshold, using the call-site line number.
	 *
	 * @return retrieval analyzer
	 */
	public static RagAnalyzer rag() {
		return (projectPath, frameworks) -> analyze(projectPath, AstAnalyzers::ragFindings,
				BuiltInAnalyzers::ragFindings);
	}

	private static List<Finding> analyze(
			Path projectPath,
			java.util.function.BiFunction<SourceFile, CompilationUnit, List<Finding>> astPerFile,
			java.util.function.Function<SourceFile, List<Finding>> regexFallback
	) {
		JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LANGUAGE_LEVEL));
		List<Finding> findings = new ArrayList<>();
		for (SourceFile source : SourceScanner.javaSources(projectPath)) {
			Optional<CompilationUnit> unit = parse(parser, source);
			if (unit.isPresent()) {
				findings.addAll(astPerFile.apply(source, unit.get()));
			}
			else {
				findings.addAll(regexFallback.apply(source));
			}
		}
		return findings;
	}

	private static Optional<CompilationUnit> parse(JavaParser parser, SourceFile source) {
		try {
			ParseResult<CompilationUnit> result = parser.parse(source.text());
			return result.isSuccessful() ? result.getResult() : Optional.empty();
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private static List<Finding> toolFindings(SourceFile source, CompilationUnit unit) {
		List<Finding> findings = new ArrayList<>();

		List<MethodDeclaration> toolMethods = unit.findAll(MethodDeclaration.class).stream()
				.filter(AstAnalyzers::hasToolAnnotation)
				.toList();
		if (toolMethods.size() >= BuiltInAnalyzers.TOOL_METHOD_THRESHOLD) {
			int line = lineOf(toolMethods.get(0));
			findings.add(Findings.atLine(
					"TOOLS-ALL-REGISTERED",
					Severity.HIGH,
					"tools",
					source,
					line,
					"This class exposes " + toolMethods.size() + " model-callable @Tool methods that are "
							+ "attached together; each enlarges the tool schema and may expose privileged actions.",
					"Group tools by intent, attach only what a classified request needs, and keep "
							+ "privileged tools off general user paths.",
					null
			));
		}

		for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
			if (isCatchAllToolRegistration(call)) {
				findings.add(Findings.atLine(
						"TOOLS-ALL-REGISTERED",
						Severity.HIGH,
						"tools",
						source,
						lineOf(call),
						"A catch-all tool collection is attached to this request path.",
						"Select a small tool set from the request intent and keep admin tools off user paths.",
						null
				));
			}
		}
		return findings;
	}

	private static List<Finding> ragFindings(SourceFile source, CompilationUnit unit) {
		List<Finding> findings = new ArrayList<>();
		for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
			if (!"topK".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
				continue;
			}
			Expression arg = call.getArgument(0);
			if (!arg.isIntegerLiteralExpr()) {
				continue;
			}
			int topK = arg.asIntegerLiteralExpr().asNumber().intValue();
			if (topK > BuiltInAnalyzers.TOP_K_THRESHOLD) {
				findings.add(Findings.atLine(
						"RAG-EXCESSIVE-TOP-K",
						Severity.MEDIUM,
						"rag",
						source,
						lineOf(call),
						"Retrieval topK=" + topK + " can inject many low-value chunks.",
						"Start with topK=4 and a similarity threshold, then tune with retrieval evaluation.",
						null
				));
			}
		}
		return findings;
	}

	private static boolean hasToolAnnotation(MethodDeclaration method) {
		for (AnnotationExpr annotation : method.getAnnotations()) {
			String name = annotation.getNameAsString();
			if ("Tool".equals(name) || name.endsWith(".Tool")) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCatchAllToolRegistration(MethodCallExpr call) {
		String name = call.getNameAsString();
		boolean registrar = "defaultToolCallbacks".equals(name)
				|| "toolCallbacks".equals(name)
				|| "defaultTools".equals(name)
				|| "tools".equals(name);
		if (!registrar || call.getArguments().size() != 1) {
			return false;
		}
		Expression arg = call.getArgument(0);
		if (!(arg instanceof NameExpr nameExpr)) {
			return false;
		}
		String identifier = nameExpr.getNameAsString().toLowerCase(Locale.ROOT);
		return "alltools".equals(identifier) || "all_tools".equals(identifier);
	}

	private static int lineOf(com.github.javaparser.ast.Node node) {
		return node.getBegin().map(position -> position.line).orElse(1);
	}
}
