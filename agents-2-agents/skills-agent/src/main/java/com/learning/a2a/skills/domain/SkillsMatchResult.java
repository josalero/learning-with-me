package com.learning.a2a.skills.domain;

import java.util.Set;

/**
 * Result of comparing candidate skills to job requirements.
 */
public record SkillsMatchResult(
		int score,
		MatchVerdict verdict,
		Set<String> matchedSkills,
		Set<String> missingSkills) {
}
