package dev.mytechprofile.tokenaudit.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.tokenaudit.Finding;
import dev.mytechprofile.tokenaudit.FindingOrigin;
import dev.mytechprofile.tokenaudit.Framework;
import dev.mytechprofile.tokenaudit.Severity;

class SemanticReviewServiceTest {

	@Test
	void review_parsesAndLabelsStructuredFindings() {
		AtomicReference<LlmReviewRequest> captured = new AtomicReference<>();
		LlmReviewClient client = request -> {
			captured.set(request);
			return new LlmReviewResponse("""
					{"findings":[{
					  "id":"duplicated-policy-context",
					  "severity":"MEDIUM",
					  "area":"prompts",
					  "location":"Demo.java:12",
					  "message":"Policy text is duplicated across prompt sections.",
					  "recommendation":"Keep one stable policy section and add a regression test.",
					  "estimatedTokens":120
					}]}
					""", "test/model", 700, 90);
		};
		EvidenceCollector collector = ignored -> new EvidenceBundle(
				List.of(new EvidenceSnippet("Demo.java", "   12 | String prompt = policy + policy;")),
				1,
				false,
				40
		);
		SemanticReviewOptions options = new SemanticReviewOptions(2, 1_000, 2_000, 4);
		SemanticReviewService service = new SemanticReviewService(client, collector, options);
		Finding deterministic = new Finding(
				"TOOLS-ALL-REGISTERED", Severity.HIGH, "tools", "Demo.java:5",
				"All tools attached", "Select tools", null
		);

		SemanticReviewResult result = service.review(
				Path.of("."), Set.of(Framework.SPRING_AI), List.of(deterministic)
		);

		Finding finding = result.findings().getFirst();
		assertEquals("AI-DUPLICATED-POLICY-CONTEXT", finding.id());
		assertEquals(FindingOrigin.AI_INFERRED, finding.origin());
		assertEquals(120, finding.estimatedTokens());
		assertEquals("test/model", result.model());
		assertTrue(captured.get().evidencePrompt().contains("TOOLS-ALL-REGISTERED"));
		assertTrue(captured.get().outputSchema().containsKey("properties"));
	}
}
