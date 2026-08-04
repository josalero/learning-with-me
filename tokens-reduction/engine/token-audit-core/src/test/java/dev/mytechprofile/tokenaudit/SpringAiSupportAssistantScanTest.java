package dev.mytechprofile.tokenaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Regression fixture: scanning the Spring AI support example must surface the five
 * intentional waste patterns with stable finding IDs.
 */
class SpringAiSupportAssistantScanTest {

	private static final Set<String> EXPECTED_IDS = Set.of(
			"PROMPT-OVERSIZED-SYSTEM",
			"TOOLS-ALL-REGISTERED",
			"RAG-EXCESSIVE-TOP-K",
			"MEMORY-UNBOUNDED-HISTORY",
			"AGENT-OVERSIZED-HANDOFF"
	);

	@Test
	void scan_springAiSupportAssistant_reportsExpectedFindings() {
		Path example = Path.of("..", "..", "examples", "spring-ai-support-assistant")
				.toAbsolutePath()
				.normalize();
		assumeTrue(Files.isDirectory(example), "example project missing at " + example);

		TokenAuditResult result = TokenAuditor.builder()
				.projectPath(example)
				.frameworks(Framework.SPRING_AI)
				.analyze();

		Set<String> ids = result.findings().stream()
				.map(Finding::id)
				.collect(Collectors.toSet());

		assertEquals(EXPECTED_IDS, ids, () -> "findings=" + result.findings());
		assertTrue(result.findings().stream()
				.anyMatch(f -> f.location().contains("SupportTools.java")
						&& f.message().contains("7 ")));
	}
}
