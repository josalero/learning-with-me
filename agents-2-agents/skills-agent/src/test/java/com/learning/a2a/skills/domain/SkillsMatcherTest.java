package com.learning.a2a.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillsMatcherTest {

	private final SkillsMatcher matcher = new SkillsMatcher();

	@Test
	void match_whenMostSkillsOverlap_returnsStrongMatch() {
		SkillsMatchResult result = matcher.match(
				"Java, Spring Boot, Azure, Kafka",
				"Java, Spring Boot, AWS, Kafka");

		assertThat(result.score()).isEqualTo(75);
		assertThat(result.verdict()).isEqualTo(MatchVerdict.STRONG_MATCH);
		assertThat(result.matchedSkills()).containsExactlyInAnyOrder("java", "spring boot", "kafka");
		assertThat(result.missingSkills()).containsExactly("aws");
	}

	@Test
	void match_whenFewSkillsOverlap_returnsWeakMatch() {
		SkillsMatchResult result = matcher.match(
				"Python, Django",
				"Java, Spring Boot, AWS, Kafka");

		assertThat(result.score()).isEqualTo(0);
		assertThat(result.verdict()).isEqualTo(MatchVerdict.WEAK_MATCH);
		assertThat(result.matchedSkills()).isEmpty();
		assertThat(result.missingSkills()).hasSize(4);
	}

	@Test
	void match_isCaseInsensitiveAndTrimsWhitespace() {
		SkillsMatchResult result = matcher.match(
				" java , SPRING BOOT ",
				"Java,Spring Boot");

		assertThat(result.score()).isEqualTo(100);
		assertThat(result.verdict()).isEqualTo(MatchVerdict.STRONG_MATCH);
	}
}
