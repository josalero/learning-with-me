package dev.mytechprofile.sdlc.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies JSON out of a reasoning model's {@code thinking} field when {@code content} is empty.
 *
 * <p><strong>When to use:</strong> wrap OpenRouter chat models. DeepSeek V4 Pro often returns
 * {@code content: null} (JSON only in thinking, or nowhere). LangChain4j then throws {@code
 * OutputParsingException: Failed to parse null into ReviewVerdict}.
 *
 * <p><strong>Example:</strong> {@code new ThinkingJsonChatModel(openAi, fallback).chat(request)}
 * always has parseable {@code text()} when the model omitted content.
 */
public final class ThinkingJsonChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ThinkingJsonChatModel.class);

    private final ChatModel delegate;
    private final String emptyFallbackJson;

    /**
     * Wraps {@code delegate} with a generic {@code \{\}} fallback.
     *
     * @param delegate OpenRouter or test chat model
     */
    public ThinkingJsonChatModel(ChatModel delegate) {
        this(delegate, "{}");
    }

    /**
     * Wraps {@code delegate} and substitutes {@code emptyFallbackJson} when both content and
     * thinking are empty.
     *
     * @param delegate OpenRouter or test chat model
     * @param emptyFallbackJson valid JSON object for this role, for example a ReviewVerdict
     */
    public ThinkingJsonChatModel(ChatModel delegate, String emptyFallbackJson) {
        this.delegate = delegate;
        this.emptyFallbackJson = emptyFallbackJson == null || emptyFallbackJson.isBlank() ? "{}" : emptyFallbackJson;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return recover(delegate.chat(chatRequest), emptyFallbackJson);
    }

    static ChatResponse recover(ChatResponse response) {
        return recover(response, "{}");
    }

    static ChatResponse recover(ChatResponse response, String emptyFallbackJson) {
        String fallback = emptyFallbackJson == null || emptyFallbackJson.isBlank() ? "{}" : emptyFallbackJson;
        if (response == null || response.aiMessage() == null) {
            log.warn("Chat response had no assistant message; using fallback JSON");
            return ChatResponse.builder().aiMessage(AiMessage.from(fallback)).build();
        }
        AiMessage message = response.aiMessage();
        if (message.hasToolExecutionRequests()) {
            return response;
        }
        if (JsonPayloads.hasText(message.text())) {
            String unwrapped = JsonPayloads.firstObject(message.text());
            if (unwrapped == null || unwrapped.equals(message.text())) {
                return response;
            }
            return replaceText(response, message, unwrapped);
        }
        String recovered = JsonPayloads.firstObject(message.thinking());
        if (recovered != null) {
            log.warn("Assistant content was empty; using JSON from the thinking field");
            return replaceText(response, message, recovered);
        }
        log.warn("Assistant content and thinking were empty; using fallback JSON");
        return replaceText(response, message, fallback);
    }

    private static ChatResponse replaceText(ChatResponse response, AiMessage message, String text) {
        return response.toBuilder()
                .aiMessage(message.toBuilder().text(text).build())
                .build();
    }
}
