package com.learning.a2a.orchestrator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Recruiter screening request accepted by the public REST API.
 */
public record ScreeningRequest(
		@NotBlank String name,
		@NotBlank String email,
		@NotBlank String jobTitle,
		@NotBlank String requiredSkills,
		@NotBlank String candidateSkills,
		@Positive int expectedSalary) {
}
