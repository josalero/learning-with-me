package com.learning.a2a.orchestrator.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScreeningRequestTest {

	@Test
	void toString_includesSkillsAndJobTitleForTheOrchestratorPrompt() {
		ScreeningRequest request = new ScreeningRequest(
				"Jane Doe",
				"jane@example.com",
				"Backend Developer",
				"Java, Spring Boot, AWS",
				"Java, Spring Boot, Azure",
				110_000);

		assertThat(request.toString())
			.contains("Backend Developer")
			.contains("Java, Spring Boot, AWS")
			.contains("Java, Spring Boot, Azure")
			.contains("110000");
	}
}
