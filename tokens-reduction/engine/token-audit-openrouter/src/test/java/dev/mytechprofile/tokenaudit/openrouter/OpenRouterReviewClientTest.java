package dev.mytechprofile.tokenaudit.openrouter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import dev.mytechprofile.tokenaudit.review.LlmReviewRequest;
import dev.mytechprofile.tokenaudit.review.LlmReviewResponse;

class OpenRouterReviewClientTest {

	@Test
	void complete_sendsStrictSchemaAndParsesUsage() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<JsonNode> requestBody = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/chat/completions", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(mapper.readTree(exchange.getRequestBody()));
			String structuredContent = mapper.writeValueAsString(Map.of("findings", java.util.List.of()));
			String response = mapper.writeValueAsString(Map.of(
					"model", "provider/concrete-model",
					"choices", java.util.List.of(Map.of(
							"message", Map.of("content", structuredContent)
					)),
					"usage", Map.of("prompt_tokens", 321, "completion_tokens", 45)
			));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		try {
			OpenRouterConfig config = new OpenRouterConfig(
					"secret-key",
					"openrouter/auto",
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
					Duration.ofSeconds(5),
					false,
					null,
					"token-audit-test"
			);
			LlmReviewRequest request = new LlmReviewRequest(
					"system", "evidence", "review", Map.of("type", "object")
			);

			LlmReviewResponse response = new OpenRouterReviewClient(config).complete(request);

			assertEquals("Bearer secret-key", authorization.get());
			assertEquals("json_schema", requestBody.get().path("response_format").path("type").asText());
			assertTrue(requestBody.get().path("response_format").path("json_schema").path("strict").asBoolean());
			assertTrue(requestBody.get().path("provider").path("require_parameters").asBoolean());
			assertEquals("deny", requestBody.get().path("provider").path("data_collection").asText());
			assertEquals("provider/concrete-model", response.model());
			assertEquals(321, response.promptTokens());
			assertEquals(45, response.completionTokens());
		}
		finally {
			server.stop(0);
		}
	}
}
