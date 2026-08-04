package dev.mytechprofile.tokenaudit.openrouter;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenRouter chat-completions configuration.
 *
 * @param apiKey bearer API key
 * @param model OpenRouter model slug
 * @param endpoint full chat-completions endpoint
 * @param timeout request timeout
 * @param allowProviderDataCollection whether providers that may retain prompts are allowed
 * @param httpReferer optional application URL attribution header
 * @param applicationTitle optional application title attribution header
 */
public record OpenRouterConfig(
		String apiKey,
		String model,
		URI endpoint,
		Duration timeout,
		boolean allowProviderDataCollection,
		String httpReferer,
		String applicationTitle
) {
	/** Validates endpoint, credential, model, and timeout configuration. */
	public OpenRouterConfig {
		apiKey = requireText(apiKey, "apiKey");
		model = requireText(model, "model");
		Objects.requireNonNull(endpoint, "endpoint");
		Objects.requireNonNull(timeout, "timeout");
		if (!"https".equalsIgnoreCase(endpoint.getScheme())
				&& !isLoopbackHttp(endpoint)) {
			throw new IllegalArgumentException("OpenRouter endpoint must use HTTPS");
		}
		if (timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("timeout must be positive");
		}
	}

	/**
	 * Creates standard production configuration with provider data collection denied.
	 *
	 * @param apiKey OpenRouter API key
	 * @param model model slug
	 * @return standard configuration
	 */
	public static OpenRouterConfig standard(String apiKey, String model) {
		return new OpenRouterConfig(
				apiKey,
				model,
				URI.create("https://openrouter.ai/api/v1/chat/completions"),
				Duration.ofSeconds(90),
				false,
				null,
				"token-audit"
		);
	}

	private static boolean isLoopbackHttp(URI endpoint) {
		String host = endpoint.getHost();
		return "http".equalsIgnoreCase(endpoint.getScheme())
				&& ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
				|| "::1".equals(host));
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
