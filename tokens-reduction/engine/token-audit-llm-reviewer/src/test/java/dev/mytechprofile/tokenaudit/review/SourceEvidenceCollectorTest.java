package dev.mytechprofile.tokenaudit.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceEvidenceCollectorTest {

	@TempDir
	Path tempDir;

	@Test
	void collect_redactsSecretsAndAddsLineNumbers() throws Exception {
		Path source = tempDir.resolve("src/main/java/DemoAgent.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, """
				class DemoAgent {
				  String apiKey = "sk-or-v1-super-secret-value";
				  void call() { ChatClient.create().prompt("hello"); }
				}
				""");

		EvidenceBundle bundle = new SourceEvidenceCollector(
				new SemanticReviewOptions(2, 2_000, 4_000, 4)
		).collect(tempDir);

		String content = bundle.snippets().getFirst().content();
		assertFalse(content.contains("super-secret-value"));
		assertTrue(content.contains("<REDACTED>"));
		assertTrue(content.contains("1 | class DemoAgent"));
		assertTrue(bundle.redactionCount() > 0);
	}

	@Test
	void collect_appliesTotalEvidenceLimit() throws Exception {
		for (int i = 0; i < 3; i++) {
			Files.writeString(
					tempDir.resolve("Agent" + i + ".java"),
					"class Agent" + i + " { String prompt = \"" + "x".repeat(500) + "\"; }"
			);
		}

		EvidenceBundle bundle = new SourceEvidenceCollector(
				new SemanticReviewOptions(3, 300, 500, 4)
		).collect(tempDir);

		assertTrue(bundle.characterCount() <= 500);
		assertTrue(bundle.truncated());
	}
}
