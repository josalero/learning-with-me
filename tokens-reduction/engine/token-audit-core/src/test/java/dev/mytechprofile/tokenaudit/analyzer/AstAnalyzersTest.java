package dev.mytechprofile.tokenaudit.analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.Framework;

class AstAnalyzersTest {

	@TempDir
	Path projectDir;

	@Test
	void tool_flagsClassWithManyToolMethods() throws IOException {
		writeJava("Tools.java", """
				package demo;
				import org.springframework.ai.tool.annotation.Tool;
				class Tools {
					@Tool String a() { return "a"; }
					@Tool String b() { return "b"; }
					@Tool String c() { return "c"; }
					@Tool String d() { return "d"; }
					@Tool String e() { return "e"; }
				}
				""");

		List<Finding> findings = AstAnalyzers.tool().analyze(projectDir, Set.of(Framework.SPRING_AI));

		assertEquals(1, findings.size());
		assertEquals("TOOLS-ALL-REGISTERED", findings.get(0).id());
		assertTrue(findings.get(0).message().contains("5 "), findings.get(0)::message);
	}

	@Test
	void tool_ignoresFewToolMethods() throws IOException {
		writeJava("Few.java", """
				package demo;
				import org.springframework.ai.tool.annotation.Tool;
				class Few {
					@Tool String a() { return "a"; }
					@Tool String b() { return "b"; }
				}
				""");

		assertEquals(List.of(), AstAnalyzers.tool().analyze(projectDir, Set.of(Framework.SPRING_AI)));
	}

	@Test
	void tool_ignoresAtToolInsideStringsAndComments() throws IOException {
		// A raw regex line-start match would over-count these; the AST does not.
		writeJava("Docs.java", """
				package demo;
				class Docs {
					// @Tool
					// @Tool
					// @Tool
					// @Tool
					// @Tool
					String template() {
						return \"\"\"
				@Tool
				@Tool
				@Tool
				@Tool
				@Tool
				\"\"\";
					}
				}
				""");

		assertEquals(List.of(), AstAnalyzers.tool().analyze(projectDir, Set.of(Framework.SPRING_AI)));
	}

	@Test
	void rag_flagsExcessiveIntegerTopK() throws IOException {
		writeJava("Rag.java", """
				package demo;
				class Rag {
					Object build(Client client) {
						return client.query().topK(12).run();
					}
				}
				""");

		List<Finding> findings = AstAnalyzers.rag().analyze(projectDir, Set.of(Framework.SPRING_AI));

		assertEquals(1, findings.size());
		assertEquals("RAG-EXCESSIVE-TOP-K", findings.get(0).id());
		assertTrue(findings.get(0).message().contains("topK=12"), findings.get(0)::message);
	}

	@Test
	void rag_ignoresSafeTopKAndCommentedTopK() throws IOException {
		writeJava("SafeRag.java", """
				package demo;
				class SafeRag {
					Object build(Client client) {
						// legacy: topK(50) was too high
						return client.query().topK(4).run();
					}
				}
				""");

		assertEquals(List.of(), AstAnalyzers.rag().analyze(projectDir, Set.of(Framework.SPRING_AI)));
	}

	@Test
	void rag_fallsBackToRegexWhenFileDoesNotParse() throws IOException {
		// Deliberately malformed Java: AST parsing fails, so the regex fallback runs.
		writeJava("Broken.java", """
				package demo;
				class Broken {
					void m( {
						client.topK(50);
				""");

		List<Finding> findings = AstAnalyzers.rag().analyze(projectDir, Set.of(Framework.SPRING_AI));

		assertEquals(1, findings.size());
		assertEquals("RAG-EXCESSIVE-TOP-K", findings.get(0).id());
	}

	private void writeJava(String name, String body) throws IOException {
		Path file = projectDir.resolve(name);
		Files.writeString(file, body);
	}
}
