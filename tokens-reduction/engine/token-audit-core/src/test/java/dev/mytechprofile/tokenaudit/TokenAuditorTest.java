package dev.mytechprofile.tokenaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenAuditorTest {

	@TempDir
	Path tempDir;

	@Test
	void analyze_returnsNoFindingsForEmptyProject() {
		TokenAuditResult result = TokenAuditor.builder()
				.projectPath(tempDir)
				.frameworks(Framework.SPRING_AI)
				.analyze();

		assertEquals(0, result.findings().size());
		assertTrue(result.frameworks().contains(Framework.SPRING_AI));
	}

	@Test
	void analyze_requiresProjectPath() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
				TokenAuditor.builder().frameworks(Framework.SPRING_AI).analyze());
		assertTrue(ex.getMessage().contains("projectPath"));
	}

	@Test
	void analyze_requiresAtLeastOneFramework() {
		IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
				TokenAuditor.builder().projectPath(tempDir).analyze());
		assertTrue(ex.getMessage().toLowerCase().contains("framework"));
	}

	@Test
	void analyze_rejectsMissingDirectory() {
		Path missing = tempDir.resolve("does-not-exist");
		IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
				TokenAuditor.builder()
						.projectPath(missing)
						.frameworks(Framework.LANGCHAIN4J)
						.analyze());
		assertTrue(ex.getMessage().contains("directory"));
	}

	@Test
	void frameworkFromCli_parsesCommonAliases() {
		assertEquals(Framework.SPRING_AI, Framework.fromCli("spring-ai"));
		assertEquals(Framework.LANGCHAIN4J, Framework.fromCli("langchain4j"));
		assertThrows(IllegalArgumentException.class, () -> Framework.fromCli("unknown"));
	}

	@Test
	void analyze_aggregatesCustomAnalyzerFindings() {
		TokenAuditResult result = TokenAuditor.builder()
				.projectPath(tempDir)
				.frameworks(Framework.SPRING_AI, Framework.LANGCHAIN4J)
				.toolAnalyzer((path, frameworks) -> List.of(
						new Finding(
								"TOOL-1",
								Severity.MEDIUM,
								"tools",
								"Demo.java",
								"All tools exposed",
								"Filter tools",
								2100
						)
				))
				.analyze();

		assertEquals(1, result.findings().size());
		assertFalse(result.findings().stream().noneMatch(f -> "TOOL-1".equals(f.id())));
	}

	@Test
	void analyze_aggregatesMultiAgentFindings() {
		TokenAuditResult result = TokenAuditor.builder()
				.projectPath(tempDir)
				.frameworks(Framework.SPRING_AI)
				.agentAnalyzer((path, frameworks) -> List.of(
						new Finding(
								"AGENT-1",
								Severity.HIGH,
								"agents",
								"Coordinator.java",
								"Child agents receive the complete parent context",
								"Create task-scoped handoff bundles",
								4000
						)
				))
				.analyze();

		assertTrue(result.findings().stream().anyMatch(f -> "AGENT-1".equals(f.id())));
	}

	@Test
	void builtInAnalyzers_detectSpringAiExampleWaste() {
		Path exampleProject = Path.of(System.getProperty("exampleProjectDir"));

		TokenAuditResult result = TokenAuditor.builder()
				.projectPath(exampleProject)
				.frameworks(Framework.SPRING_AI)
				.analyze();
		Set<String> ids = result.findings().stream()
				.map(Finding::id)
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"PROMPT-OVERSIZED-SYSTEM",
				"TOOLS-ALL-REGISTERED",
				"RAG-EXCESSIVE-TOP-K",
				"MEMORY-UNBOUNDED-HISTORY",
				"AGENT-OVERSIZED-HANDOFF"
		), ids);
	}

	@Test
	void finding_legacyConstructorMarksDeterministicOrigin() {
		Finding finding = new Finding(
				"TEST", Severity.INFO, "engine", "Demo.java", "message", "recommendation", null
		);

		assertEquals(FindingOrigin.DETERMINISTIC, finding.origin());
	}
}
