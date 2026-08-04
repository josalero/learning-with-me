package dev.mytechprofile.tokenaudit;

/**
 * AI frameworks the auditor can target.
 */
public enum Framework {
	SPRING_AI,
	LANGCHAIN4J;

	/**
	 * Parses a CLI-friendly name such as {@code spring-ai} or {@code langchain4j}.
	 *
	 * @param value framework name
	 * @return matching framework
	 * @throws IllegalArgumentException if unknown
	 */
	public static Framework fromCli(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("framework must not be blank");
		}
		String normalized = value.trim().toLowerCase().replace('_', '-');
		return switch (normalized) {
			case "spring-ai", "springai" -> SPRING_AI;
			case "langchain4j", "langchain-4j" -> LANGCHAIN4J;
			default -> throw new IllegalArgumentException("Unknown framework: " + value);
		};
	}
}
