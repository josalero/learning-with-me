package dev.mytechprofile.sdlc.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.mytechprofile.sdlc.catalog.RoleSpec;

/**
 * Builds per-role OpenRouter chat models from a {@link RoleSpec}.
 *
 * <p><strong>When to use:</strong> production runs. {@code sdlc.offline=true} returns
 * {@link ScriptedChatModel}. Tests may also inject a scripted {@link ChatModel}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * ChatModel model = factory.modelFor(developerRole);
 * }</pre>
 */
public final class ChatModelFactory {

    private final SdlcProperties properties;

    /**
     * Creates a factory using OpenRouter settings from {@code properties}.
     *
     * @param properties API key, base URL, max tokens, and per-request timeout
     */
    public ChatModelFactory(SdlcProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns a chat model for {@code role}.
     *
     * @param role team role with model slug and temperature
     * @return OpenAI-compatible OpenRouter client
     */
    public ChatModel modelFor(RoleSpec role) {
        if (properties.offline()) {
            return ScriptedChatModel.INSTANCE;
        }
        if (properties.openrouterApiKey() == null
                || properties.openrouterApiKey().isBlank()) {
            throw new IllegalStateException(
                    "OPENROUTER_API_KEY is not set. Copy .env.example to .env and add a key from https://openrouter.ai/keys, or set SDLC_OFFLINE=true");
        }
        var builder = OpenAiChatModel.builder()
                .apiKey(properties.openrouterApiKey())
                .baseUrl(properties.openrouterBaseUrl())
                .modelName(role.model())
                .temperature(role.temperature())
                .maxTokens(properties.maxTokens())
                .timeout(properties.llmTimeout())
                .maxRetries(2)
                .returnThinking(true)
                .logRequests(false)
                .logResponses(false);
        if (usesJsonObjectResponse(role)) {
            builder.responseFormat("json_object");
        }
        return new ThinkingJsonChatModel(builder.build(), emptyFallbackJson(role));
    }

    /**
     * {@code json_object} plus reasoning models often yield {@code content: null}. Keep it for
     * fast roles; skip it for the PR Reviewer on {@code MODEL_STRONG}.
     */
    static boolean usesJsonObjectResponse(RoleSpec role) {
        return switch (role.kind()) {
            case DEVELOPER, TECH_LEAD, PR_REVIEWER -> false;
            case PRODUCT_OWNER, QA, STAKEHOLDER -> true;
        };
    }

    static String emptyFallbackJson(RoleSpec role) {
        return switch (role.kind()) {
            case PR_REVIEWER -> "{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}";
            case QA -> "{\"decision\":\"FAIL\",\"score\":0,\"results\":[],\"missingTests\":[]}";
            case STAKEHOLDER -> "{\"decision\":\"APPROVED\",\"reasons\":[],\"followUps\":[]}";
            case PRODUCT_OWNER ->
                "{\"title\":\"Requested feature\",\"problem\":\"\",\"userStories\":[],\"acceptanceCriteria\":[],\"outOfScope\":[],\"priority\":\"should\"}";
            case DEVELOPER, TECH_LEAD -> "{}";
        };
    }
}
