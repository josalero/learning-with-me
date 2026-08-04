package dev.mytechprofile.tokenaudit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.Severity;
import dev.mytechprofile.tokenaudit.TokenAuditResult;

import org.junit.jupiter.api.Test;

class ReportFormatterTest {

	private static TokenAuditResult resultWithTrickyFinding() {
		Finding finding = new Finding(
				"PROMPT-OVERSIZED-SYSTEM",
				Severity.MEDIUM,
				"prompts",
				"src/App.java:10",
				"Prompt uses a | pipe and a \"quote\"\nand a newline.",
				"Trim it.",
				330);
		return new TokenAuditResult(Path.of("/proj"), Set.of(Framework.SPRING_AI), List.of(finding));
	}

	@Test
	void json_escapesControlCharactersAndQuotes() {
		String json = ReportFormatter.render(resultWithTrickyFinding(), ReportFormat.JSON);

		assertTrue(json.contains("\"findingCount\": 1"), "count should be present");
		assertTrue(json.contains("\"estimatedTokens\": 330"), "numeric token estimate should be raw");
		assertTrue(json.contains("\\\"quote\\\""), "quotes must be escaped");
		assertTrue(json.contains("\\n"), "newlines must be escaped");
		assertFalse(json.contains("pipe and a \"quote\""), "raw unescaped quote must not leak");
	}

	@Test
	void markdown_escapesPipesAndFlattensNewlines() {
		String markdown = ReportFormatter.render(resultWithTrickyFinding(), ReportFormat.MARKDOWN);

		assertTrue(markdown.contains("# Token Efficiency Audit Report"), "has title");
		assertTrue(markdown.contains("| High / Medium / Low | 0 / 1 / 0 |"), "severity tallies");
		assertTrue(markdown.contains("\\|"), "table cells must escape pipes");
		assertFalse(markdown.contains("pipe and a | pipe"), "unescaped pipe must not split the cell");
	}

	@Test
	void text_matchesConsoleShape() {
		String text = ReportFormatter.render(resultWithTrickyFinding(), ReportFormat.TEXT);

		assertTrue(text.contains("[MEDIUM] PROMPT-OVERSIZED-SYSTEM (prompts) @ src/App.java:10"));
		assertTrue(text.contains("1 finding(s) in /proj"));
	}

	@Test
	void reportFormat_inferenceAndParsing() {
		assertEquals(ReportFormat.JSON, ReportFormat.fromFile(Path.of("a/b/report.json")));
		assertEquals(ReportFormat.MARKDOWN, ReportFormat.fromFile(Path.of("report.md")));
		assertEquals(ReportFormat.TEXT, ReportFormat.fromFile(Path.of("report.log")));
		assertEquals(ReportFormat.TEXT, ReportFormat.fromFile(Path.of("noext")));
		assertEquals(ReportFormat.MARKDOWN, ReportFormat.fromName("markdown"));
	}
}
