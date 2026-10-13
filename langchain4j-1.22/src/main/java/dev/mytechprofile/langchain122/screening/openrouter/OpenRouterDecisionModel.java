package dev.mytechprofile.langchain122.screening.openrouter;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Speaks OpenRouter's Decisions API and returns LangChain4j decision answers.
 *
 * <p>Call this when {@code OPENROUTER_API_KEY} is set and the screen should run on
 * {@code openai/gpt-6-luna-decisions}. {@code OpenAiDecisionModel} posts OpenAI's questions
 * array to {@code {baseUrl}/decisions}. OpenRouter serves a questions object and a
 * {@code state} field at {@code POST /api/alpha/decisions}. Building this client does not
 * send the application. {@link #doDecide} does.
 *
 * <p>This class owns only HTTP. {@link OpenRouterRequestMapper} writes the body and
 * {@link OpenRouterResponseMapper} reads the answers, so each mapping is testable without a socket.
 *
 * <pre>{@code
 * DecisionModel model = new OpenRouterDecisionModel(
 *         apiKey,
 *         OpenRouterDecisionModel.GPT_6_LUNA_DECISIONS,
 *         OpenRouterDecisionModel.DECISIONS_ENDPOINT);
 * ApplicationScreen screen = DecisionServices.create(ApplicationScreen.class, model);
 * ScreenResult result = screen.screen(
 *         ImageContent.from(AtsSample.PORTFOLIO_PNG, "image/png"),
 *         AtsSample.RECRUITER_NOTE);
 * }</pre>
 *
 * A 401, a timeout, a missing answer, or a mismatched {@code type} throws
 * {@link OpenRouterDecisionException}. The exception text does not include the note or the key.
 */
public final class OpenRouterDecisionModel implements DecisionModel {

    /** Decisions URL. Chat completions use {@code https://openrouter.ai/api/v1} instead. */
    public static final URI DECISIONS_ENDPOINT = URI.create("https://openrouter.ai/api/alpha/decisions");

    /** GPT-6 Luna on OpenRouter's Decisions API. The chat id {@code openai/gpt-6-luna} is a different model. */
    public static final String GPT_6_LUNA_DECISIONS = "openai/gpt-6-luna-decisions";

    /** How long {@link #doDecide} waits for the response body. Connect timeout is 5 seconds. */
    public static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final String modelName;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final OpenRouterRequestMapper requestMapper = new OpenRouterRequestMapper();
    private final OpenRouterResponseMapper responseMapper = new OpenRouterResponseMapper();

    /**
     * @param apiKey OpenRouter bearer token. A blank key is rejected here so a request is never sent as {@code Bearer }
     * @param modelName catalog id, for example {@link #GPT_6_LUNA_DECISIONS}
     * @param endpoint full decisions URL, normally {@link #DECISIONS_ENDPOINT}
     */
    public OpenRouterDecisionModel(String apiKey, String modelName, URI endpoint) {
        this(apiKey, modelName, endpoint, defaultHttpClient());
    }

    /**
     * Same as the public constructor with a caller-owned client, for proxies, custom TLS, or tests.
     *
     * @param httpClient should not follow redirects, so the bearer token never reaches another host
     */
    public OpenRouterDecisionModel(String apiKey, String modelName, URI endpoint, HttpClient httpClient) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenRouter API key is blank. Set OPENROUTER_API_KEY.");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("OpenRouter decision model id is blank.");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("OpenRouter HttpClient is null.");
        }
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.endpoint = endpoint == null ? DECISIONS_ENDPOINT : endpoint;
        this.httpClient = httpClient;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    /**
     * Posts one decisions request and maps each answer back onto the question LangChain4j asked.
     *
     * <p>Not idempotent: OpenRouter treats each call as a new, billed generation.
     * A non-2xx status, a timeout, or a body that is missing {@code answers} fails the call.
     * The response body is not attached to the exception.
     *
     * @param request named input plus the yes/no, choice, and scale questions from {@code DecisionServices}
     * @return one answer per question, plus token counts when {@code usage} includes them
     */
    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        String body = post(requestMapper.toJson(request, modelName));
        return responseMapper.fromJson(request, body);
    }

    private String post(String json) {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(RESPONSE_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = send(httpRequest);
        if (response.statusCode() / 100 != 2) {
            throw new OpenRouterDecisionException("OpenRouter decisions HTTP " + response.statusCode());
        }
        if (response.body() == null || response.body().isBlank()) {
            throw new OpenRouterDecisionException("OpenRouter decisions returned an empty body");
        }
        return response.body();
    }

    private HttpResponse<String> send(HttpRequest httpRequest) {
        try {
            return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException failure) {
            throw new OpenRouterDecisionException("OpenRouter decisions timed out", failure);
        } catch (IOException failure) {
            throw new OpenRouterDecisionException("OpenRouter decisions unreachable", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new OpenRouterDecisionException("OpenRouter decisions interrupted", failure);
        }
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
