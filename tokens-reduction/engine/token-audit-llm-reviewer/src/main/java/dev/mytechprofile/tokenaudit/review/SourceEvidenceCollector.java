package dev.mytechprofile.tokenaudit.review;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Selects likely AI-related source files, redacts common secret shapes, and applies
 * strict per-file and total character limits.
 */
public final class SourceEvidenceCollector implements EvidenceCollector {

	private static final Set<String> EXTENSIONS = Set.of(
			".java", ".kt", ".properties", ".yaml", ".yml", ".json", ".txt", ".st", ".tmpl"
	);
	private static final List<String> RELEVANCE_TERMS = List.of(
			"chatclient", "chatmodel", "langchain4j", "prompt", "system_message",
			"systemprompt", "topk", "vectorstore", "retriev", "tool", "memory",
			"agent", "delegate", "handoff", "token"
	);
	private static final Pattern LABELED_SECRET = Pattern.compile(
			"(?i)\\b(api[_-]?key|client[_-]?secret|password|access[_-]?token)\\b"
					+ "(\\s*[:=]\\s*)([\"']?)([^\\s\"',;}]+)([\"']?)"
	);
	private static final Pattern BEARER_SECRET = Pattern.compile(
			"(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}"
	);
	private static final Pattern KEY_SHAPE = Pattern.compile(
			"\\b(?:sk-or-v1-|sk-)[A-Za-z0-9_-]{12,}"
	);

	private final SemanticReviewOptions options;

	/**
	 * Creates an evidence collector using the supplied limits.
	 *
	 * @param options evidence limits
	 */
	public SourceEvidenceCollector(SemanticReviewOptions options) {
		this.options = java.util.Objects.requireNonNull(options, "options");
	}

	@Override
	public EvidenceBundle collect(Path projectPath) {
		if (!Files.isDirectory(projectPath)) {
			throw new LlmReviewException("Evidence project path is not a directory: " + projectPath);
		}

		List<Candidate> candidates = new ArrayList<>(discover(projectPath));
		candidates.sort(Comparator
				.comparingInt(Candidate::score)
				.reversed()
				.thenComparing(Candidate::relativePath));

		List<EvidenceSnippet> snippets = new ArrayList<>();
		int redactions = 0;
		int totalCharacters = 0;
		boolean truncated = candidates.size() > options.maxFiles();
		for (Candidate candidate : candidates) {
			if (snippets.size() >= options.maxFiles() || totalCharacters >= options.maxTotalCharacters()) {
				truncated = true;
				break;
			}

			Redaction redaction = redact(candidate.content());
			redactions += redaction.count();
			String lineNumbered = addLineNumbers(redaction.content());
			int available = Math.min(
					options.maxCharactersPerFile(),
					options.maxTotalCharacters() - totalCharacters
			);
			if (lineNumbered.length() > available) {
				String marker = "\n... [TRUNCATED]";
				lineNumbered = available > marker.length()
						? lineNumbered.substring(0, available - marker.length()) + marker
						: lineNumbered.substring(0, available);
				truncated = true;
			}
			if (candidate.truncated()) {
				truncated = true;
			}
			snippets.add(new EvidenceSnippet(candidate.relativePath(), lineNumbered));
			totalCharacters += lineNumbered.length();
		}

		return new EvidenceBundle(snippets, redactions, truncated, totalCharacters);
	}

	private List<Candidate> discover(Path projectPath) {
		try (Stream<Path> paths = Files.walk(projectPath)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> isSupported(path.getFileName().toString()))
					.filter(path -> !isExcluded(projectPath.relativize(path)))
					.map(path -> candidate(projectPath, path))
					.filter(candidate -> candidate.score() > 0)
					.toList();
		}
		catch (IOException ex) {
			throw new LlmReviewException("Failed to discover semantic-review evidence", ex);
		}
	}

	private Candidate candidate(Path projectPath, Path path) {
		ReadResult read = readBounded(path, options.maxCharactersPerFile() * 2);
		String lowered = read.content().toLowerCase(Locale.ROOT);
		int score = 0;
		for (String term : RELEVANCE_TERMS) {
			int index = lowered.indexOf(term);
			while (index >= 0) {
				score++;
				index = lowered.indexOf(term, index + term.length());
			}
		}
		return new Candidate(
				projectPath.relativize(path).toString().replace('\\', '/'),
				read.content(),
				score,
				read.truncated()
		);
	}

	private ReadResult readBounded(Path path, int limit) {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			StringBuilder content = new StringBuilder(Math.min(limit, 8_192));
			char[] buffer = new char[2_048];
			boolean truncated = false;
			while (content.length() < limit) {
				int requested = Math.min(buffer.length, limit - content.length());
				int read = reader.read(buffer, 0, requested);
				if (read < 0) {
					return new ReadResult(content.toString(), false);
				}
				content.append(buffer, 0, read);
			}
			truncated = reader.read() >= 0;
			return new ReadResult(content.toString(), truncated);
		}
		catch (IOException ex) {
			throw new LlmReviewException("Failed to read semantic-review evidence: " + path, ex);
		}
	}

	private static Redaction redact(String content) {
		Redaction first = replace(content, LABELED_SECRET, matcher ->
				matcher.group(1) + matcher.group(2) + matcher.group(3)
						+ "<REDACTED>" + matcher.group(5));
		Redaction second = replace(first.content(), BEARER_SECRET, matcher -> "Bearer <REDACTED>");
		Redaction third = replace(second.content(), KEY_SHAPE, matcher -> "<REDACTED>");
		return new Redaction(
				third.content(),
				first.count() + second.count() + third.count()
		);
	}

	private static Redaction replace(String input, Pattern pattern, Replacement replacement) {
		Matcher matcher = pattern.matcher(input);
		StringBuilder output = new StringBuilder(input.length());
		int count = 0;
		while (matcher.find()) {
			matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.value(matcher)));
			count++;
		}
		matcher.appendTail(output);
		return new Redaction(output.toString(), count);
	}

	private static String addLineNumbers(String content) {
		String[] lines = content.split("\\R", -1);
		StringBuilder numbered = new StringBuilder(content.length() + (lines.length * 8));
		for (int i = 0; i < lines.length; i++) {
			numbered.append(String.format("%5d | %s%n", i + 1, lines[i]));
		}
		return numbered.toString();
	}

	private static boolean isSupported(String filename) {
		String lowered = filename.toLowerCase(Locale.ROOT);
		return EXTENSIONS.stream().anyMatch(lowered::endsWith);
	}

	private static boolean isExcluded(Path relativePath) {
		for (Path part : relativePath) {
			String name = part.toString();
			if (name.startsWith(".") || "build".equals(name) || "target".equals(name)
					|| "node_modules".equals(name)) {
				return true;
			}
		}
		String normalized = relativePath.toString().replace('\\', '/');
		return normalized.contains("/src/test/") || normalized.startsWith("src/test/");
	}

	@FunctionalInterface
	private interface Replacement {
		String value(Matcher matcher);
	}

	private record Candidate(String relativePath, String content, int score, boolean truncated) {
	}

	private record ReadResult(String content, boolean truncated) {
	}

	private record Redaction(String content, int count) {
	}
}
