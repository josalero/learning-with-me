package dev.mytechprofile.tokenaudit.cli;

import java.time.LocalDate;
import java.util.List;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.FindingOrigin;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.Severity;
import dev.mytechprofile.tokenaudit.TokenAuditResult;

/**
 * Renders a {@link TokenAuditResult} as text, JSON, Markdown, or HTML for on-disk reports
 * and the console.
 */
public final class ReportFormatter {

	private ReportFormatter() {
	}

	/**
	 * Renders the result in the requested format (files: no ANSI).
	 *
	 * @param result the audit result
	 * @param format the desired format
	 * @return the rendered report body
	 */
	public static String render(TokenAuditResult result, ReportFormat format) {
		return switch (format) {
			case TEXT -> text(result, false);
			case JSON -> json(result);
			case MARKDOWN -> markdown(result);
			case HTML -> html(result);
		};
	}

	/**
	 * Renders console-oriented text, optionally with ANSI severity colors.
	 *
	 * @param result the audit result
	 * @param color whether to apply ANSI colors
	 * @return console text
	 */
	public static String text(TokenAuditResult result, boolean color) {
		StringBuilder out = new StringBuilder();
		for (Finding finding : result.findings()) {
			String origin = finding.origin() == FindingOrigin.AI_INFERRED ? " [AI-INFERRED]" : "";
			out.append(SeverityPalette.ansiTag(finding.severity(), color))
					.append(SeverityPalette.ansiOrigin(origin, color))
					.append(' ')
					.append(SeverityPalette.ansiId(finding.id(), color))
					.append(SeverityPalette.ansiMuted(
							" (" + finding.area() + ") @ " + finding.location(), color))
					.append('\n')
					.append("  ").append(finding.message()).append('\n')
					.append("  ")
					.append(SeverityPalette.ansiRecommendation("→ " + finding.recommendation(), color))
					.append('\n');
		}
		out.append('\n')
				.append(SeverityPalette.ansiMuted(
						result.findings().size() + " finding(s) in " + result.projectPath(), color))
				.append('\n');
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
			out.append("      \"severityColor\": ").append(quote(SeverityPalette.htmlColor(finding.severity())))
					.append(",\n");
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
		long high = count(findings, Severity.HIGH);
		long medium = count(findings, Severity.MEDIUM);
		long low = count(findings, Severity.LOW);
		String frameworks = String.join(", ",
				result.frameworks().stream().map(ReportFormatter::frameworkName).toList());

		out.append("# Token Efficiency Audit Report\n\n");
		out.append("| Field | Value |\n| --- | --- |\n");
		out.append("| Project | `").append(result.projectPath()).append("` |\n");
		out.append("| Date | ").append(LocalDate.now()).append(" |\n");
		out.append("| Frameworks | ").append(frameworks.isEmpty() ? "—" : frameworks).append(" |\n");
		out.append("| Findings | ").append(findings.size()).append(" |\n");
		out.append("| High / Medium / Low | ")
				.append("🔴 ").append(high)
				.append(" / 🟠 ").append(medium)
				.append(" / 🟡 ").append(low).append(" |\n\n");

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
					.append(SeverityPalette.markdown(finding.severity())).append(" | ")
					.append(finding.area()).append(" | `")
					.append(finding.location()).append("` | ")
					.append(cell(finding.message())).append(" | ")
					.append(cell(finding.recommendation())).append(" |\n");
		}
		return out.toString();
	}

	private static String html(TokenAuditResult result) {
		List<Finding> findings = result.findings();
		long high = count(findings, Severity.HIGH);
		long medium = count(findings, Severity.MEDIUM);
		long low = count(findings, Severity.LOW);
		String frameworks = escapeHtml(String.join(", ",
				result.frameworks().stream().map(ReportFormatter::frameworkName).toList()));

		StringBuilder out = new StringBuilder();
		out.append("""
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8" />
				<meta name="viewport" content="width=device-width, initial-scale=1" />
				<title>Token Efficiency Audit Report</title>
				<style>
				  :root {
				    --bg: #0f172a; --panel: #111827; --card: #1e293b; --text: #e2e8f0;
				    --muted: #94a3b8; --border: #334155; --accent: #38bdf8;
				  }
				  * { box-sizing: border-box; }
				  body {
				    margin: 0; padding: 32px 20px 48px;
				    font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
				    background: radial-gradient(1200px 600px at 10% -10%, #1e3a5f 0%, var(--bg) 55%);
				    color: var(--text); line-height: 1.5;
				  }
				  main { max-width: 960px; margin: 0 auto; }
				  h1 { font-size: 1.75rem; margin: 0 0 8px; letter-spacing: -0.02em; }
				  .sub { color: var(--muted); margin-bottom: 24px; }
				  .summary {
				    display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 28px;
				  }
				  .stat {
				    background: var(--card); border: 1px solid var(--border); border-radius: 12px;
				    padding: 14px 16px;
				  }
				  .stat .label { color: var(--muted); font-size: 0.75rem; text-transform: uppercase;
				    letter-spacing: 0.06em; }
				  .stat .value { font-size: 1.35rem; font-weight: 700; margin-top: 4px; }
				  .meta {
				    background: var(--panel); border: 1px solid var(--border); border-radius: 12px;
				    padding: 16px 18px; margin-bottom: 24px; font-size: 0.92rem;
				  }
				  .meta code { color: var(--accent); font-size: 0.85rem; word-break: break-all; }
				  .finding {
				    background: var(--card); border: 1px solid var(--border); border-left-width: 5px;
				    border-radius: 12px; padding: 16px 18px; margin-bottom: 12px;
				  }
				  .finding-head { display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
				    margin-bottom: 8px; }
				  .pill {
				    display: inline-block; border-radius: 999px; padding: 2px 10px;
				    font-size: 0.72rem; font-weight: 700; letter-spacing: 0.04em;
				  }
				  .id { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-weight: 700; }
				  .loc { color: var(--muted); font-size: 0.85rem; font-family: ui-monospace, monospace; }
				  .msg { margin: 8px 0; }
				  .rec { color: #86efac; font-size: 0.92rem; }
				  .empty { color: var(--muted); padding: 24px; text-align: center;
				    border: 1px dashed var(--border); border-radius: 12px; }
				  @media (max-width: 720px) { .summary { grid-template-columns: 1fr 1fr; } }
				</style>
				</head>
				<body>
				<main>
				""");
		out.append("<h1>Token Efficiency Audit Report</h1>\n");
		out.append("<p class=\"sub\">Colored severity view · Spring AI scan</p>\n");

		out.append("<div class=\"summary\">\n");
		out.append(stat("Findings", String.valueOf(findings.size()), "#e2e8f0"));
		out.append(stat("High", String.valueOf(high), SeverityPalette.htmlColor(Severity.HIGH)));
		out.append(stat("Medium", String.valueOf(medium), SeverityPalette.htmlColor(Severity.MEDIUM)));
		out.append(stat("Low", String.valueOf(low), SeverityPalette.htmlColor(Severity.LOW)));
		out.append("</div>\n");

		out.append("<div class=\"meta\">\n");
		out.append("<div><strong>Project</strong><br/><code>")
				.append(escapeHtml(result.projectPath().toString())).append("</code></div>\n");
		out.append("<div style=\"margin-top:10px\"><strong>Date</strong> ")
				.append(LocalDate.now())
				.append(" · <strong>Frameworks</strong> ")
				.append(frameworks.isEmpty() ? "—" : frameworks)
				.append("</div>\n");
		out.append("</div>\n");

		if (findings.isEmpty()) {
			out.append("<div class=\"empty\">No findings.</div>\n");
		}
		else {
			for (Finding finding : findings) {
				String color = SeverityPalette.htmlColor(finding.severity());
				String bg = SeverityPalette.htmlBackground(finding.severity());
				String origin = finding.origin() == FindingOrigin.AI_INFERRED
						? " · AI-INFERRED" : "";
				out.append("<article class=\"finding\" style=\"border-left-color:")
						.append(color).append("\">\n");
				out.append("<div class=\"finding-head\">\n");
				out.append("<span class=\"pill\" style=\"color:")
						.append(color).append(";background:")
						.append(bg).append("\">")
						.append(finding.severity().name())
						.append("</span>\n");
				out.append("<span class=\"id\">")
						.append(escapeHtml(finding.id())).append("</span>\n");
				out.append("<span class=\"pill\" style=\"background:#334155;color:#cbd5e1\">")
						.append(escapeHtml(finding.area())).append(origin).append("</span>\n");
				out.append("</div>\n");
				out.append("<div class=\"loc\">")
						.append(escapeHtml(finding.location())).append("</div>\n");
				out.append("<p class=\"msg\">")
						.append(escapeHtml(finding.message())).append("</p>\n");
				out.append("<p class=\"rec\">→ ")
						.append(escapeHtml(finding.recommendation())).append("</p>\n");
				if (finding.estimatedTokens() != null) {
					out.append("<p class=\"loc\">~")
							.append(finding.estimatedTokens())
							.append(" tokens</p>\n");
				}
				out.append("</article>\n");
			}
		}

		out.append("""
				</main>
				</body>
				</html>
				""");
		return out.toString();
	}

	private static String stat(String label, String value, String color) {
		return "<div class=\"stat\"><div class=\"label\">" + escapeHtml(label)
				+ "</div><div class=\"value\" style=\"color:" + color + "\">"
				+ escapeHtml(value) + "</div></div>\n";
	}

	private static long count(List<Finding> findings, Severity severity) {
		return findings.stream().filter(f -> f.severity() == severity).count();
	}

	private static String frameworkName(Framework framework) {
		return framework.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	private static String cell(String value) {
		return value.replace("|", "\\|").replace("\n", " ");
	}

	private static String escapeHtml(String value) {
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

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
