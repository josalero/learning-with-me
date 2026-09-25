package dev.mytechprofile.jev.decide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.channel.ChannelOption;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.mytechprofile.jev.config.OpenRouterProperties;

class OpenRouterJevDeciderTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void decidePostsChoiceQuestionAndReadsSeniorLabel() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "answers": {
                            "seniority": {
                              "choice": "SENIOR",
                              "confidence": 0.91,
                              "probabilities": {
                                "JUNIOR": 0.02,
                                "INTERMEDIATE": 0.07,
                                "SENIOR": 0.91
                              },
                              "type": "choice"
                            }
                          },
                          "id": "gen-dec-test",
                          "model": "typesafe/jev-1.13"
                        }
                        """));
        OpenRouterJevDecider decider = decider();

        SeniorityDecision decision = decider.decide("10 years of experience. Mentors engineers.");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/alpha/decisions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(request.getBody().readUtf8())
                .contains("\"model\":\"typesafe/jev-1.13\"")
                .contains("\"type\":\"choice\"")
                .contains("\"JUNIOR\"")
                .contains("Use only the resume field.")
                .contains("bootcamps, or guided tasks.")
                .doesNotContain("\"questions\":[");
        assertThat(decision.level()).isEqualTo(Seniority.SENIOR);
        assertThat(decision.confidence()).isEqualTo(0.91);
        assertThat(decision.probabilities()).containsEntry(Seniority.INTERMEDIATE, 0.07);
        assertThat(decision.decider()).isEqualTo("jev");
    }

    @Test
    void unknownChoiceFailsWithoutEchoingTheResume() {
        JsonNodeAnswer answer = new JsonNodeAnswer(jsonMapper);
        assertThatThrownBy(() -> OpenRouterJevDecider.parse(answer.root()))
                .isInstanceOf(JevClientException.class)
                .hasMessage("Jev returned an unknown seniority label");
    }

    @Test
    void httpErrorDoesNotIncludeTheResponseBody() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("secret-body"));
        OpenRouterJevDecider decider = decider();

        assertThatThrownBy(() -> decider.decide("resume text"))
                .isInstanceOf(JevClientException.class)
                .hasMessage("OpenRouter decisions HTTP 401")
                .hasMessageNotContaining("secret-body")
                .hasMessageNotContaining("resume text");
    }

    @Test
    void malformedJsonBecomesJevClientException() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("not-json"));

        assertThatThrownBy(() -> decider().decide("resume text"))
                .isInstanceOf(JevClientException.class)
                .hasMessage("OpenRouter decisions returned invalid JSON")
                .hasMessageNotContaining("not-json")
                .hasMessageNotContaining("resume text");
    }

    @Test
    void connectionFailureBecomesJevClientException() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500);
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        OpenRouterJevDecider decider = new OpenRouterJevDecider(
                properties("http://127.0.0.1:" + port),
                webClient,
                jsonMapper,
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> decider.decide("resume text"))
                .isInstanceOf(JevClientException.class)
                .hasMessage("OpenRouter decisions unreachable")
                .hasMessageNotContaining("resume text");
    }

    @Test
    void slowResponseBecomesJevClientException() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBodyDelay(2, TimeUnit.SECONDS)
                .setBody("{\"answers\":{}}"));
        OpenRouterJevDecider decider = new OpenRouterJevDecider(
                properties(server.url("/").toString().replaceAll("/$", "")),
                WebClient.builder().baseUrl(server.url("/").toString().replaceAll("/$", "")).build(),
                jsonMapper,
                Duration.ofMillis(200));

        assertThatThrownBy(() -> decider.decide("resume text"))
                .isInstanceOf(JevClientException.class)
                .hasMessage("OpenRouter decisions timed out")
                .hasMessageNotContaining("resume text");
    }

    @Test
    void confidenceAboveOneIsRejected() {
        JsonNode root = jsonMapper.readTree("""
                {"answers":{"seniority":{"choice":"SENIOR","confidence":1.5,"probabilities":{}}}}
                """);

        assertThatThrownBy(() -> OpenRouterJevDecider.parse(root))
                .isInstanceOf(JevClientException.class)
                .hasMessage("Jev returned an invalid seniority decision");
    }

    private OpenRouterJevDecider decider() {
        OpenRouterProperties properties = properties(server.url("/").toString().replaceAll("/$", ""));
        WebClient webClient = WebClient.builder().baseUrl(properties.baseUrl()).build();
        return new OpenRouterJevDecider(properties, webClient, jsonMapper, Duration.ofSeconds(5));
    }

    private static OpenRouterProperties properties(String baseUrl) {
        return new OpenRouterProperties(
                "test-key",
                baseUrl,
                "/alpha/decisions",
                "typesafe/jev-1.13",
                "https://openrouter.ai/api/v1",
                "openai/gpt-4o-mini",
                true);
    }

    private record JsonNodeAnswer(JsonMapper jsonMapper) {
        tools.jackson.databind.JsonNode root() {
            return jsonMapper.readTree("""
                    {"answers":{"seniority":{"choice":"STAFF","confidence":0.5,"probabilities":{}}}}
                    """);
        }
    }
}
