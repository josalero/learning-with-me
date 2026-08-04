package dev.mytechprofile.tokenaudit.openrouter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.mytechprofile.tokenaudit.review.LlmReviewClient;
import dev.mytechprofile.tokenaudit.review.LlmReviewException;
import dev.mytechprofile.tokenaudit.review.LlmReviewRequest;
import dev.mytechprofile.tokenaudit.review.LlmReviewResponse;

/**
 * OpenRouter adapter using its OpenAI-compatible chat-completions endpoint.
 */
public final class OpenRouterReviewClient implements LlmReviewClient {

	private final OpenRouterConfig config;
	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	/**
	 * Creates an OpenRouter client with the JDK HTTP transport.
	 *
	 * @param config endpoint, model, credential, privacy, and timeout settings
	 */
	public OpenRouterReviewClient(OpenRouterConfig config) {
		this(
				config,
				HttpClient.newBuilder()
						.connectTimeout(Objects.requireNonNull(config, "config").timeout())
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build(),
				new ObjectMapper()
		);
	}

	OpenRouterReviewClient(
			OpenRouterConfig config,
			HttpClient httpClient,
			ObjectMapper objectMapper
	) {
		this.config = Objects.requireNonNull(config, "config");
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
	}

	@Override
	public LlmReviewResponse complete(LlmReviewRequest request) {
		try {
			String requestJson = objectMapper.writeValueAsString(requestBody(request));
			HttpRequest.Builder builder = HttpRequest.newBuilder(config.endpoint())
					.timeout(config.timeout())
					.header("Authorization", "Bearer " + config.apiKey())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestJson));
			if (config.httpReferer() != null && !config.httpReferer().isBlank()) {
				builder.header("HTTP-Referer", config.httpReferer());
			}
			if (config.applicationTitle() != null && !config.applicationTitle().isBlank()) {
				builder.header("X-OpenRouter-Title", config.applicationTitle());
			}

			HttpResponse<String> response = httpClient.send(
					builder.build(),
					HttpResponse.BodyHandlers.ofString()
			);
			return parseResponse(response.statusCode(), response.body());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LlmReviewException("OpenRouter review was interrupted", ex);
		}
		catch (IOException ex) {
			throw new LlmReviewException("OpenRouter request failed", ex);
		}
	}

	private Map<String, Object> requestBody(LlmReviewRequest request) {
		Map<String, Object> jsonSchema = new LinkedHashMap<>();
		jsonSchema.put("name", request.schemaName());
		jsonSchema.put("strict", true);
		jsonSchema.put("schema", request.outputSchema());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", config.model());
		body.put("messages", List.of(
				Map.of("role", "system", "content", request.systemPrompt()),
				Map.of("role", "user", "content", request.evidencePrompt())
		));
		body.put("response_format", Map.of(
				"type", "json_schema",
				"json_schema", jsonSchema
		));
		body.put("provider", Map.of(
				"require_parameters", true,
				"data_collection", config.allowProviderDataCollection() ? "allow" : "deny"
		));
		body.put("max_completion_tokens", 2_500);
		body.put("stream", false);
		return body;
	}

	private LlmReviewResponse parseResponse(int statusCode, String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
			if (statusCode < 200 || statusCode >= 300 || root.hasNonNull("error")) {
				throw new LlmReviewException(openRouterError(statusCode, root));
			}
			JsonNode choice = root.path("choices").path(0);
			if (choice.hasNonNull("error")) {
				throw new LlmReviewException(openRouterError(statusCode, choice));
			}
			JsonNode contentNode = choice.path("message").path("content");
			if (!contentNode.isTextual() || contentNode.asText().isBlank()) {
				throw new LlmReviewException("OpenRouter response did not contain message content");
			}
			JsonNode usage = root.path("usage");
			return new LlmReviewResponse(
					contentNode.asText(),
					root.path("model").asText(config.model()),
					nullableInt(usage, "prompt_tokens"),
					nullableInt(usage, "completion_tokens")
			);
		}
		catch (LlmReviewException ex) {
			throw ex;
		}
		catch (JsonProcessingException ex) {
			throw new LlmReviewException("OpenRouter returned invalid JSON", ex);
		}
	}

	private static Integer nullableInt(JsonNode node, String field) {
		return node.path(field).isIntegralNumber() ? node.path(field).intValue() : null;
	}

	private static String openRouterError(int statusCode, JsonNode root) {
		JsonNode error = root.path("error");
		if (error.isMissingNode()) {
			error = root.path("choices").path(0).path("error");
		}
		String type = error.path("metadata").path("error_type").asText("");
		String message = error.path("message").asText("OpenRouter request failed");
		return "OpenRouter error (HTTP " + statusCode
				+ (type.isBlank() ? "" : ", " + type) + "): " + message;
	}
}
