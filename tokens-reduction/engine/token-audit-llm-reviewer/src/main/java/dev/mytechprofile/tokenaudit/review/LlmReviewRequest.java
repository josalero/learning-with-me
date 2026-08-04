package dev.mytechprofile.tokenaudit.review;

import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral structured completion request.
 *
 * @param systemPrompt stable reviewer instructions
 * @param evidencePrompt redacted source evidence and deterministic findings
 * @param schemaName output schema name
 * @param outputSchema JSON Schema object
 */
public record LlmReviewRequest(
		String systemPrompt,
		String evidencePrompt,
		String schemaName,
		Map<String, Object> outputSchema
) {
	/** Validates and copies the provider-neutral request. */
	public LlmReviewRequest {
		systemPrompt = requireText(systemPrompt, "systemPrompt");
		evidencePrompt = requireText(evidencePrompt, "evidencePrompt");
		schemaName = requireText(schemaName, "schemaName");
		outputSchema = Map.copyOf(Objects.requireNonNull(outputSchema, "outputSchema"));
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
