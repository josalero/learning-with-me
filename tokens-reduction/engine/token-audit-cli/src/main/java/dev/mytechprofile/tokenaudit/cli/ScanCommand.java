package dev.mytechprofile.tokenaudit.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.TokenAuditResult;
import dev.mytechprofile.tokenaudit.TokenAuditor;
import dev.mytechprofile.tokenaudit.openrouter.OpenRouterConfig;
import dev.mytechprofile.tokenaudit.openrouter.OpenRouterReviewClient;
import dev.mytechprofile.tokenaudit.review.SemanticReviewOptions;
import dev.mytechprofile.tokenaudit.review.SemanticReviewResult;
import dev.mytechprofile.tokenaudit.review.SemanticReviewService;
import dev.mytechprofile.tokenaudit.review.SourceEvidenceCollector;
import dev.mytechprofile.tokenaudit.review.LlmReviewException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Scans a project directory for token-efficiency findings.
 */
@Command(name = "scan", description = "Scan a project for token-efficiency issues.")
public final class ScanCommand implements Callable<Integer> {

	@Parameters(index = "0", description = "Project root directory.", defaultValue = ".")
	private Path projectPath;

	@Option(
			names = {"--framework", "-f"},
			description = "Framework to analyze (spring-ai, langchain4j). Repeatable.",
			split = ","
	)
	private List<String> frameworks = new ArrayList<>(List.of("spring-ai"));

	@Option(
			names = {"--out", "-o"},
			description = "Write a report file; repeat for multiple. Format is inferred from the "
					+ "extension (.json, .md, .txt) unless --format is set. Console output is unaffected."
	)
	private List<Path> outputs = new ArrayList<>();

	@Option(
			names = "--format",
			description = "Explicit report format for every --out file (text|json|md|html). "
					+ "Default: inferred from each file's extension."
	)
	private String format;

	@Option(
			names = "--color",
			description = "Console colors: auto (default), always, never. "
					+ "Also respects NO_COLOR / FORCE_COLOR.",
			defaultValue = "auto"
	)
	private String color;

	@Option(
			names = "--llm-review",
			description = "Opt in to sending a bounded, redacted evidence bundle to an external LLM."
	)
	private boolean llmReview;

	@Option(
			names = "--llm-provider",
			description = "Semantic-review provider (currently: openrouter).",
			defaultValue = "openrouter"
	)
	private String llmProvider;

	@Option(
			names = "--llm-model",
			description = "Provider model slug; defaults to OPENROUTER_MODEL or openrouter/auto."
	)
	private String llmModel;

	@Option(names = "--llm-max-files", defaultValue = "20")
	private int llmMaxFiles;

	@Option(names = "--llm-max-file-chars", defaultValue = "8000")
	private int llmMaxFileCharacters;

	@Option(names = "--llm-max-evidence-chars", defaultValue = "40000")
	private int llmMaxEvidenceCharacters;

	@Option(names = "--llm-max-findings", defaultValue = "8")
	private int llmMaxFindings;

	@Option(
			names = "--llm-allow-provider-data-collection",
			description = "Allow routing to providers that may retain prompts; denied by default."
	)
	private boolean llmAllowProviderDataCollection;

	@Override
	public Integer call() {
		try {
			TokenAuditor.Builder builder = TokenAuditor.builder().projectPath(projectPath);
			Framework first = Framework.fromCli(frameworks.getFirst());
			Framework[] rest = frameworks.stream()
					.skip(1)
					.map(Framework::fromCli)
					.toArray(Framework[]::new);
			TokenAuditResult result = builder.frameworks(first, rest).analyze();
			SemanticReviewResult semanticReview = null;
			if (llmReview) {
				semanticReview = runSemanticReview(result);
				List<Finding> combined = new ArrayList<>(result.findings());
				combined.addAll(semanticReview.findings());
				result = new TokenAuditResult(result.projectPath(), result.frameworks(), combined);
				System.out.printf(
						"LLM review: model=%s, evidenceFiles=%d, redactions=%d, truncated=%s, "
								+ "promptTokens=%s, completionTokens=%s%n%n",
						semanticReview.model(),
						semanticReview.evidenceFiles(),
						semanticReview.redactionCount(),
						semanticReview.evidenceTruncated(),
						semanticReview.promptTokens(),
						semanticReview.completionTokens()
				);
			}
			System.out.print(ReportFormatter.text(result, consoleColorEnabled()));
			writeReports(result);
			return 0;
		}
		catch (IllegalArgumentException | IllegalStateException | LlmReviewException ex) {
			System.err.println("error: " + ex.getMessage());
			return 2;
		}
		catch (UncheckedIOException ex) {
			System.err.println("error: could not write report: " + ex.getCause().getMessage());
			return 2;
		}
	}

	private boolean consoleColorEnabled() {
		return switch (color == null ? "auto" : color.trim().toLowerCase(Locale.ROOT)) {
			case "always", "on", "yes", "true" -> true;
			case "never", "off", "no", "false" -> false;
			default -> SeverityPalette.ansiEnabled();
		};
	}

	private void writeReports(TokenAuditResult result) {
		if (outputs.isEmpty()) {
			return;
		}
		ReportFormat explicit = (format == null || format.isBlank())
				? null
				: ReportFormat.fromName(format);
		for (Path output : outputs) {
			ReportFormat chosen = explicit != null ? explicit : ReportFormat.fromFile(output);
			String body = ReportFormatter.render(result, chosen);
			try {
				Path parent = output.toAbsolutePath().getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.writeString(output, body);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			System.out.printf("report: %s (%s)%n", output, chosen.name().toLowerCase());
		}
	}

	private SemanticReviewResult runSemanticReview(TokenAuditResult deterministic) {
		if (!"openrouter".equalsIgnoreCase(llmProvider)) {
			throw new IllegalArgumentException("Unsupported LLM provider: " + llmProvider);
		}
		String apiKey = System.getenv("OPENROUTER_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException(
					"OPENROUTER_API_KEY is required when --llm-review is enabled"
			);
		}
		String model = firstNonBlank(llmModel, System.getenv("OPENROUTER_MODEL"), "openrouter/auto");
		SemanticReviewOptions options = new SemanticReviewOptions(
				llmMaxFiles,
				llmMaxFileCharacters,
				llmMaxEvidenceCharacters,
				llmMaxFindings
		);
		OpenRouterConfig standard = OpenRouterConfig.standard(apiKey, model);
		OpenRouterConfig config = new OpenRouterConfig(
				standard.apiKey(),
				standard.model(),
				standard.endpoint(),
				standard.timeout(),
				llmAllowProviderDataCollection,
				standard.httpReferer(),
				standard.applicationTitle()
		);

		System.err.printf(
				"LLM review enabled: up to %d files / %d redacted source characters "
						+ "will be sent to OpenRouter using %s.%n",
				options.maxFiles(),
				options.maxTotalCharacters(),
				model
		);
		SemanticReviewService service = new SemanticReviewService(
				new OpenRouterReviewClient(config),
				new SourceEvidenceCollector(options),
				options
		);
		return service.review(
				deterministic.projectPath(),
				deterministic.frameworks(),
				deterministic.findings()
		);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		throw new IllegalArgumentException("at least one non-blank value is required");
	}
}
