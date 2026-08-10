package com.learning.a2a.orchestrator.api;

import com.learning.a2a.orchestrator.api.dto.ScreeningRequest;
import com.learning.a2a.orchestrator.api.dto.ScreeningResponse;
import com.learning.a2a.orchestrator.application.ScreeningService;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST boundary for candidate screening.
 *
 * <pre>
 * POST /api/v1/screenings
 * {
 *   "name": "Jane Doe",
 *   "email": "jane@example.com",
 *   "jobTitle": "Backend Developer",
 *   "requiredSkills": "Java, Spring Boot, AWS, Kafka",
 *   "candidateSkills": "Java, Spring Boot, Azure, Kafka",
 *   "expectedSalary": 110000
 * }
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/v1/screenings", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class ScreeningController {

	private final ScreeningService screeningService;

	public ScreeningController(ScreeningService screeningService) {
		this.screeningService = screeningService;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ScreeningResponse screenCandidate(@Valid @RequestBody ScreeningRequest request) {
		return screeningService.screen(request);
	}
}
