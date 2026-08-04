package dev.mytechprofile.tokenaudit.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JtokkitTokenEstimatorTest {

	@Test
	void cl100k_countsKnownPhraseDeterministically() {
		// Stable OpenAI cl100k_base count for a short English phrase.
		assertEquals(3, JtokkitTokenEstimator.cl100k().estimate("Hello, world"));
	}

	@Test
	void cl100k_treatsNullAndEmptyAsZero() {
		assertEquals(0, JtokkitTokenEstimator.cl100k().estimate(null));
		assertEquals(0, JtokkitTokenEstimator.cl100k().estimate(""));
	}

	@Test
	void cl100k_countsLargePromptAboveCharHeuristic() {
		String prompt = "You are a helpful assistant. ".repeat(40);
		int tokens = JtokkitTokenEstimator.cl100k().estimate(prompt);
		int charHeuristic = Math.max(1, (prompt.length() + 3) / 4);

		assertTrue(tokens > 0, "should count tokens");
		// Real BPE counts diverge from length/4; this guards against accidental fallback.
		assertTrue(tokens != charHeuristic || prompt.length() < 50,
				"jtokkit count should use BPE, not length/4 (tokens=" + tokens
						+ ", heuristic=" + charHeuristic + ")");
	}

	@Test
	void encodingName_matchesEncodingType() {
		assertEquals("cl100k_base", JtokkitTokenEstimator.cl100k().encodingName());
		assertEquals("o200k_base", JtokkitTokenEstimator.o200k().encodingName());
	}
}
