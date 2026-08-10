package com.learning.a2a.orchestrator.api;

import java.util.Map;

import com.learning.a2a.orchestrator.a2a.A2aCommunicationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps application failures to RFC 9457 Problem Details for the public API.
 */
@RestControllerAdvice
public class ScreeningApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST,
				"Request validation failed");
		problem.setTitle("Bad Request");
		problem.setProperty(
				"errors",
				ex.getBindingResult().getFieldErrors().stream()
					.map(error -> Map.of(
							"field", error.getField(),
							"message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
					.toList());
		return problem;
	}

	@ExceptionHandler(A2aCommunicationException.class)
	ProblemDetail handleA2a(A2aCommunicationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
		problem.setTitle("Remote agent communication failed");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Bad Request");
		return problem;
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail handleIllegalState(IllegalStateException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setTitle("Service Unavailable");
		return problem;
	}
}
