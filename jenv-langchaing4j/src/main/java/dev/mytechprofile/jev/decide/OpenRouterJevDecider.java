package dev.mytechprofile.jev.decide;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.mytechprofile.jev.config.OpenRouterProperties;

/**
 * Calls {@code POST /api/alpha/decisions} and reads the {@code seniority} choice.
 *
 * <p>{@code questions} is a map keyed by name, which is the Decisions API shape.
 * Instructions and criteria are loaded from {@code classpath:jev/}.
 * The body shape is documented in {@code docs/jev-decisions.md}. The response timeout
 * is {@link #RESPONSE_TIMEOUT} unless a test passes a shorter duration.
 * A connect timeout of 5 seconds is set on the shared {@code WebClient}.
 * HTTP errors, connection failures, timeouts, an empty body, invalid JSON,
 * an unknown choice, and a confidence outside 0 to 1 throw
 * {@link JevClientException} without the resume or the upstream payload.
 *
 * <pre>{@code
 * // answers.seniority.choice = SENIOR, confidence = 0.91
 * decider.decide("10 years of experience. Mentors engineers.");
 * }</pre>
 */
public final class OpenRouterJevDecider implements SeniorityDecider {

    static final String DECIDER = "jev";
    static final String QUESTION = "seniority";

    private static final String INSTRUCTIONS = read("jev/instructions.txt");
    private static final Map<String, String> CRITERIA = criteria();

    /**
     * How long {@code block} waits for the Decisions response. {@code ClientConfig}
     * also sets this as the WebClient response timeout.
     */
    public static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(20);

    private final OpenRouterProperties properties;
    private final WebClient webClient;
    private final JsonMapper jsonMapper;
    private final Duration responseTimeout;

    /**
     * @param properties model id, decisions path, and API key
     * @param webClient client whose base URL is {@code openrouter.base-url}
     * @param jsonMapper parser for the Decisions JSON body
     * @param responseTimeout how long to wait for the body; production uses {@link #RESPONSE_TIMEOUT}
     */
    public OpenRouterJevDecider(
            OpenRouterProperties properties,
            WebClient webClient,
            JsonMapper jsonMapper,
            Duration responseTimeout) {
        this.properties = properties;
        this.webClient = webClient;
        this.jsonMapper = jsonMapper;
        this.responseTimeout = Objects.requireNonNull(responseTimeout, "responseTimeout");
    }

    /**
     * Posts the resume as {@code state.resume} and maps {@code answers.seniority}.
     *
     * @param resumeText plain text placed in the Decisions {@code state}
     * @return decision whose {@code decider} is {@code jev}
     * @throws JevClientException on HTTP errors, connection failures, timeouts, an empty body,
     *     invalid JSON, a choice outside the enum, or a confidence outside 0 to 1
     */
    @Override
    public SeniorityDecision decide(String resumeText) {
        String body = post(resumeText);
        if (body == null || body.isBlank()) {
            throw new JevClientException("OpenRouter decisions returned an empty body");
        }
        try {
            return parse(jsonMapper.readTree(body));
        } catch (JacksonException ex) {
            throw new JevClientException("OpenRouter decisions returned invalid JSON", ex);
        }
    }

    private String post(String resumeText) {
        try {
            return webClient.post()
                    .uri(properties.decisionsPath())
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody(resumeText))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(responseTimeout);
        } catch (WebClientResponseException ex) {
            throw new JevClientException("OpenRouter decisions HTTP " + ex.getStatusCode().value(), ex);
        } catch (WebClientRequestException ex) {
            throw new JevClientException("OpenRouter decisions unreachable", ex);
        } catch (IllegalStateException ex) {
            throw new JevClientException("OpenRouter decisions timed out", ex);
        }
    }

    Map<String, Object> requestBody(String resumeText) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", INSTRUCTIONS);
        question.put("criteria", CRITERIA);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.jevModel());
        body.put("state", Map.of("resume", resumeText));
        body.put("questions", Map.of(QUESTION, question));
        return body;
    }

    static SeniorityDecision parse(JsonNode root) {
        JsonNode answer = root.path("answers").path(QUESTION);
        String choice = answer.path("choice").asString("").trim().toUpperCase(Locale.ROOT);
        Seniority level;
        try {
            level = Seniority.valueOf(choice);
        } catch (IllegalArgumentException ex) {
            throw new JevClientException("Jev returned an unknown seniority label", ex);
        }
        double confidence = answer.path("confidence").asDouble(0);
        try {
            return new SeniorityDecision(level, confidence, probabilities(answer.path("probabilities")), DECIDER);
        } catch (IllegalArgumentException ex) {
            throw new JevClientException("Jev returned an invalid seniority decision", ex);
        }
    }

    private static Map<String, String> criteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        for (Seniority level : Seniority.values()) {
            criteria.put(level.name(), read("jev/criteria/" + level.name() + ".txt"));
        }
        return Map.copyOf(criteria);
    }

    private static String read(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException ex) {
            throw new UncheckedIOException("Missing Jev question text " + classpath, ex);
        }
    }

    private static Map<Seniority, Double> probabilities(JsonNode node) {
        Map<Seniority, Double> probabilities = new EnumMap<>(Seniority.class);
        for (Seniority seniority : Seniority.values()) {
            probabilities.put(seniority, node.path(seniority.name()).asDouble(0));
        }
        return Map.copyOf(probabilities);
    }
}
