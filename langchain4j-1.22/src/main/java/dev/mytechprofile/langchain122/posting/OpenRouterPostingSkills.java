package dev.mytechprofile.langchain122.posting;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.mytechprofile.langchain122.provider.ProviderEnvironment;
import java.util.Optional;

/**
 * Reads must-have skills through OpenRouter's OpenAI-compatible chat endpoint.
 *
 * <p>Chat completions live at {@code https://openrouter.ai/api/v1}. LangChain4j posts
 * {@code chat/completions} under that base URL, so {@link OpenAiChatModel#builder()} with
 * {@link #CHAT_BASE_URL} is enough. Decisions use a different URL and body. See
 * {@code OpenRouterDecisionModel}.
 *
 * <pre>{@code
 * ChatModel chat = OpenRouterPostingSkills.chatModel(
 *         apiKey, OpenRouterPostingSkills.DEFAULT_CHAT_MODEL);
 * PostingSkills skills = AiServices.create(PostingSkills.class, chat);
 * String line = skills.extract(TextContent.from(AtsSample.POSTING));
 * }</pre>
 *
 * <p>Building the client does not send the posting. {@link PostingSkills#extract} does.
 * Requests and responses are not logged. Override the model with {@code OPENROUTER_CHAT_MODEL}.
 * A missing key makes {@link #whenKeyPresent} empty. A later HTTP failure comes back as
 * the LangChain4j chat exception for that status.
 */
public final class OpenRouterPostingSkills {

    /** OpenAI-compatible origin. The path {@code chat/completions} is appended by LangChain4j. */
    public static final String CHAT_BASE_URL = "https://openrouter.ai/api/v1";

    /** Same default the sibling Jev lab uses for explanations. */
    public static final String DEFAULT_CHAT_MODEL = "openai/gpt-4o-mini";

    /** Bearer token variable shared with the OpenRouter decisions sample. */
    public static final String API_KEY = "OPENROUTER_API_KEY";

    /** Optional override for {@link #DEFAULT_CHAT_MODEL}. */
    public static final String MODEL = "OPENROUTER_CHAT_MODEL";

    private OpenRouterPostingSkills() {}

    /**
     * @param env where to read {@link #API_KEY} and {@link #MODEL}
     * @return an extractor aimed at OpenRouter, or empty when the key is missing
     */
    public static Optional<PostingSkills> whenKeyPresent(ProviderEnvironment env) {
        return env.value(API_KEY).map(apiKey -> AiServices.create(
                PostingSkills.class, chatModel(apiKey, env.valueOr(MODEL, DEFAULT_CHAT_MODEL))));
    }

    /**
     * Builds the chat client. Does not send the posting.
     *
     * @param apiKey OpenRouter bearer token
     * @param modelName catalog id, for example {@code openai/gpt-4o-mini}
     * @return a model whose default model name is {@code modelName} and whose base URL is {@link #CHAT_BASE_URL}
     */
    public static ChatModel chatModel(String apiKey, String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(CHAT_BASE_URL)
                .modelName(modelName)
                .temperature(0.0)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
