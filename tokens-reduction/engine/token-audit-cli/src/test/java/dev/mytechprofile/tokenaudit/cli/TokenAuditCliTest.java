package dev.mytechprofile.tokenaudit.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class TokenAuditCliTest {

	@TempDir
	Path tempDir;

	@Test
	void scan_invokesCoreAndPrintsCleanSummary() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		try {
			int code = new CommandLine(new TokenAuditCli())
					.execute("scan", tempDir.toString(), "--framework", "spring-ai");
			assertEquals(0, code);
			String printed = out.toString(StandardCharsets.UTF_8);
			assertTrue(printed.contains("0 finding(s)"));
		}
		finally {
			System.setOut(original);
		}
	}

	@Test
	void scan_returnsUsageError_forUnknownFramework() {
		int code = new CommandLine(new TokenAuditCli())
				.execute("scan", tempDir.toString(), "--framework", "nope");
		assertEquals(2, code);
	}

	@Test
	void scan_writesReportFiles_inferringFormatFromExtension() throws Exception {
		Path json = tempDir.resolve("out/report.json");
		Path markdown = tempDir.resolve("out/report.md");
		Path html = tempDir.resolve("out/report.html");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrintStream original = System.out;
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		try {
			int code = new CommandLine(new TokenAuditCli()).execute(
					"scan", tempDir.toString(), "--framework", "spring-ai",
					"--out", json.toString(), "--out", markdown.toString(),
					"--out", html.toString());
			assertEquals(0, code);
		}
		finally {
			System.setOut(original);
		}

		assertTrue(Files.exists(json), "JSON report should be created");
		assertTrue(Files.exists(markdown), "Markdown report should be created");
		assertTrue(Files.exists(html), "HTML report should be created");
		String jsonBody = Files.readString(json);
		assertTrue(jsonBody.contains("\"findingCount\": 0"), "JSON should report zero findings");
		String markdownBody = Files.readString(markdown);
		assertTrue(markdownBody.contains("# Token Efficiency Audit Report"), "Markdown should have a title");
		assertTrue(Files.readString(html).contains("<!doctype html>"), "HTML should be a document");
		assertTrue(out.toString(StandardCharsets.UTF_8).contains("report:"), "console should note each file");
	}

	@Test
	void scan_explicitFormatOverridesExtension() throws Exception {
		Path file = tempDir.resolve("report.txt");
		int code = new CommandLine(new TokenAuditCli()).execute(
				"scan", tempDir.toString(), "--out", file.toString(), "--format", "json");
		assertEquals(0, code);
		assertTrue(Files.readString(file).trim().startsWith("{"), "explicit --format json should win over .txt");
	}

	@Test
	void scan_rejectsUnknownReportFormat() {
		Path file = tempDir.resolve("report.out");
		int code = new CommandLine(new TokenAuditCli()).execute(
				"scan", tempDir.toString(), "--out", file.toString(), "--format", "xml");
		assertEquals(2, code);
	}

	@Test
	void scan_rejectsUnknownSemanticReviewProviderBeforeNetworkUse() {
		int code = new CommandLine(new TokenAuditCli()).execute(
				"scan",
				tempDir.toString(),
				"--llm-review",
				"--llm-provider",
				"unknown"
		);

		assertEquals(2, code);
	}
}
