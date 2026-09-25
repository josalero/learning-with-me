package dev.mytechprofile.jev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * OpenRouter settings for the Jev Decisions call and the optional explanation model.
 *
 * <p>Bound from {@code openrouter.*} in {@code application.yml}. The API key comes
 * from {@code OPENROUTER_API_KEY}. Leave it blank to stay offline. A blank URL or
 * model id fails startup.
 *
 * @param apiKey bearer token; blank disables live calls
 * @param baseUrl Decisions origin, default {@code https://openrouter.ai/api}
 * @param decisionsPath path appended to {@code baseUrl}, default {@code /alpha/decisions}
 * @param jevModel Decisions {@code model}, default {@code typesafe/jev-1.13}
 * @param chatBaseUrl OpenAI-compatible origin for the explainer, default {@code https://openrouter.ai/api/v1}
 * @param chatModel explainer model id, default {@code openai/gpt-4o-mini}
 * @param explain when {@code false}, a live key still calls Jev and the explanation stays on the template
 */
@Validated
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String apiKey,
        @NotBlank String baseUrl,
        @NotBlank String decisionsPath,
        @NotBlank String jevModel,
        @NotBlank String chatBaseUrl,
        @NotBlank String chatModel,
        boolean explain) {

    /**
     * Whether the key is present so Jev and the chat model may be called.
     *
     * @return {@code true} when {@code apiKey} is non-null and not blank
     */
    public boolean liveCallsEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
