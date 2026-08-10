package com.learning.a2a.skills.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Deterministic skills overlap scoring — no LLM involvement.
 *
 * <p>Framework adapters (Spring AI tools) call this service; keep scoring rules here so they stay
 * unit-testable without ChatClient.
 */
@Service
public class SkillsMatcher {

	public SkillsMatchResult match(String candidateSkillsCsv, String requiredSkillsCsv) {
		Set<String> candidate = normalize(candidateSkillsCsv);
		Set<String> required = normalize(requiredSkillsCsv);

		Set<String> matched = required.stream()
			.filter(candidate::contains)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<String> missing = required.stream()
			.filter(skill -> !candidate.contains(skill))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		int score = required.isEmpty() ? 0 : (int) Math.round((matched.size() * 100.0) / required.size());
		return new SkillsMatchResult(score, verdictFor(score), matched, missing);
	}

	private static Set<String> normalize(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static MatchVerdict verdictFor(int score) {
		if (score >= 75) {
			return MatchVerdict.STRONG_MATCH;
		}
		if (score >= 40) {
			return MatchVerdict.PARTIAL_MATCH;
		}
		return MatchVerdict.WEAK_MATCH;
	}
}
