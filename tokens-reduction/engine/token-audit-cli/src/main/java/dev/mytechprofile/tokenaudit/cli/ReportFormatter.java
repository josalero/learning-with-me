package dev.mytechprofile.tokenaudit.cli;

import java.time.LocalDate;
import java.util.List;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.FindingOrigin;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.TokenAuditResult;

/**
 * Renders a {@link TokenAuditResult} as text, JSON, or Markdown for on-disk reports.
 */
public final class ReportFormatter {

	private ReportFormatter() {
	}

	/**
	 * Renders the result in the requested format.
	 *
	 * @param result the audit result
	 * @param format the desired format
	 * @return the rendered report body
	 */
	public static String render(TokenAuditResult result, ReportFormat format) {
		return switch (format) {
			case TEXT -> text(result);
			case JSON -> json(result);
			case MARKDOWN -> markdown(result);
		};
	}

	private static String text(TokenAuditResult result) {
		StringBuilder out = new StringBuilder();
		for (Finding finding : result.findings()) {
			String origin = finding.origin() == FindingOrigin.AI_INFERRED ? " [AI-INFERRED]" : "";
			out.append(String.format(
					"[%s]%s %s (%s) @ %s%n  %s%n  -> %s%n",
					finding.severity(),
					origin,
					finding.id(),
					finding.area(),
					finding.location(),
					finding.message(),
					finding.recommendation()));
		}
		out.append(String.format(
				"%n%d finding(s) in %s%n", result.findings().size(), result.projectPath()));
		return out.toString();
	}

	private static String json(TokenAuditResult result) {
		StringBuilder out = new StringBuilder();
		out.append("{\n");
		out.append("  \"project\": ").append(quote(result.projectPath().toString())).append(",\n");
		out.append("  \"frameworks\": [");
		List<String> frameworks = result.frameworks().stream().map(ReportFormatter::frameworkName).toList();
		for (int i = 0; i < frameworks.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(quote(frameworks.get(i)));
		}
		out.append("],\n");
		out.append("  \"findingCount\": ").append(result.findings().size()).append(",\n");
		out.append("  \"findings\": [\n");
		List<Finding> findings = result.findings();
		for (int i = 0; i < findings.size(); i++) {
			Finding finding = findings.get(i);
			out.append("    {\n");
			out.append("      \"id\": ").append(quote(finding.id())).append(",\n");
			out.append("      \"severity\": ").append(quote(finding.severity().name())).append(",\n");
			out.append("      \"area\": ").append(quote(finding.area())).append(",\n");
			out.append("      \"location\": ").append(quote(finding.location())).append(",\n");
			out.append("      \"message\": ").append(quote(finding.message())).append(",\n");
			out.append("      \"recommendation\": ").append(quote(finding.recommendation())).append(",\n");
			out.append("      \"estimatedTokens\": ")
					.append(finding.estimatedTokens() == null ? "null" : finding.estimatedTokens())
					.append(",\n");
			out.append("      \"origin\": ").append(quote(finding.origin().name())).append("\n");
			out.append(i < findings.size() - 1 ? "    },\n" : "    }\n");
		}
		out.append("  ]\n");
		out.append("}\n");
		return out.toString();
	}

	private static String markdown(TokenAuditResult result) {
		StringBuilder out = new StringBuilder();
		List<Finding> findings = result.findings();
		long high = findings.stream().filter(f -> "HIGH".equals(f.severity().name())).count();
		long medium = findings.stream().filter(f -> "MEDIUM".equals(f.severity().name())).count();
		long low = findings.stream().filter(f -> "LOW".equals(f.severity().name())).count();
		String frameworks = String.join(", ",
				result.frameworks().stream().map(ReportFormatter::frameworkName).toList());

		out.append("# Token Efficiency Audit Report\n\n");
		out.append("| Field | Value |\n| --- | --- |\n");
		out.append("| Project | `").append(result.projectPath()).append("` |\n");
		out.append("| Date | ").append(LocalDate.now()).append(" |\n");
		out.append("| Frameworks | ").append(frameworks.isEmpty() ? "—" : frameworks).append(" |\n");
		out.append("| Findings | ").append(findings.size()).append(" |\n");
		out.append("| High / Medium / Low | ")
				.append(high).append(" / ").append(medium).append(" / ").append(low).append(" |\n\n");

		out.append("## Findings\n\n");
		if (findings.isEmpty()) {
			out.append("No findings.\n");
			return out.toString();
		}
		out.append("| ID | Severity | Area | Location | Issue | Recommendation |\n");
		out.append("| --- | --- | --- | --- | --- | --- |\n");
		for (Finding finding : findings) {
			String origin = finding.origin() == FindingOrigin.AI_INFERRED ? " (AI-INFERRED)" : "";
			out.append("| `").append(finding.id()).append('`').append(origin).append(" | ")
					.append(finding.severity()).append(" | ")
					.append(finding.area()).append(" | `")
					.append(finding.location()).append("` | ")
					.append(cell(finding.message())).append(" | ")
					.append(cell(finding.recommendation())).append(" |\n");
		}
		return out.toString();
	}

	private static String frameworkName(Framework framework) {
		return framework.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	/** Escapes a value for a Markdown table cell. */
	private static String cell(String value) {
		return value.replace("|", "\\|").replace("\n", " ");
	}

	/** Serializes a string as a JSON string literal. */
	private static String quote(String value) {
		StringBuilder out = new StringBuilder(value.length() + 2);
		out.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
		return out.toString();
	}
}
