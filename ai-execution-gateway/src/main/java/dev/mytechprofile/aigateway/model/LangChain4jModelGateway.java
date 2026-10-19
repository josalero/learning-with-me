package dev.mytechprofile.aigateway.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.mytechprofile.aigateway.config.GatewayProperties;
import dev.mytechprofile.aigateway.persistence.ModelPricingEntity;
import dev.mytechprofile.aigateway.persistence.ModelPricingRepository;
import jakarta.annotation.PostConstruct;

/**
 * OpenRouter chat through LangChain4j. Tool calls are returned to the orchestrator and are not executed here.
 */
@Component
@Profile("live")
public class LangChain4jModelGateway implements ModelGateway {

    static final String CHAT_BASE_URL = "https://openrouter.ai/api/v1";

    private final ChatModel chatModel;
    private final String modelId;
    private final int maxOutputTokens;
    private final ModelPricingRepository pricing;
    private final int inputMicrosPer1k;
    private final int outputMicrosPer1k;

    @Autowired
    public LangChain4jModelGateway(GatewayProperties properties, ModelPricingRepository pricing) {
        this(
                openRouter(System.getenv("OPENROUTER_API_KEY"), properties.pricing().live().modelId()),
                properties.pricing().live().modelId(),
                properties.model().maxOutputTokens(),
                pricing,
                properties.pricing().live().inputMicrosPer1k(),
                properties.pricing().live().outputMicrosPer1k());
    }

    LangChain4jModelGateway(
            ChatModel chatModel,
            String modelId,
            int maxOutputTokens,
            ModelPricingRepository pricing,
            int inputMicrosPer1k,
            int outputMicrosPer1k) {
        this.chatModel = chatModel;
        this.modelId = modelId;
        this.maxOutputTokens = maxOutputTokens;
        this.pricing = pricing;
        this.inputMicrosPer1k = inputMicrosPer1k;
        this.outputMicrosPer1k = outputMicrosPer1k;
    }

    @PostConstruct
    void requireLiveSettings() {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("OPENROUTER_MODEL is required for the live profile");
        }
        ModelPricingEntity row = pricing.findById(modelId).orElseGet(ModelPricingEntity::new);
        row.setModelId(modelId);
        row.setInputMicrosPer1k(inputMicrosPer1k);
        row.setOutputMicrosPer1k(outputMicrosPer1k);
        pricing.save(row);
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int estimatedInputTokens(List<ConversationMessage> messages) {
        int chars = 0;
        for (ConversationMessage message : messages) {
            if (message.content() != null) {
                chars += message.content().length();
            }
        }
        return Math.max(1, chars / 4);
    }

    @Override
    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    @Override
    public ModelTurn complete(String candidateId, List<ConversationMessage> messages) {
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(toChatMessages(messages))
                .toolSpecifications(ToolCatalog.specifications())
                .maxOutputTokens(maxOutputTokens)
                .build());
        AiMessage ai = response.aiMessage();
        List<ProposedToolCall> calls = new ArrayList<>();
        if (ai != null && ai.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                calls.add(new ProposedToolCall(request.id(), request.name(), request.arguments()));
            }
        }
        int input = response.tokenUsage() == null || response.tokenUsage().inputTokenCount() == null
                ? estimatedInputTokens(messages)
                : response.tokenUsage().inputTokenCount();
        int output = response.tokenUsage() == null || response.tokenUsage().outputTokenCount() == null
                ? 0
                : response.tokenUsage().outputTokenCount();
        return new ModelTurn(calls, input, output);
    }

    static List<ChatMessage> toChatMessages(List<ConversationMessage> messages) {
        List<ChatMessage> mapped = new ArrayList<>();
        int index = 0;
        while (index < messages.size()) {
            ConversationMessage message = messages.get(index);
            if (message.role() == ConversationMessage.Role.ASSISTANT && message.toolName() != null) {
                List<ToolExecutionRequest> calls = new ArrayList<>();
                while (index < messages.size()
                        && messages.get(index).role() == ConversationMessage.Role.ASSISTANT
                        && messages.get(index).toolName() != null) {
                    ConversationMessage call = messages.get(index);
                    calls.add(ToolExecutionRequest.builder()
                            .id(call.toolCallId())
                            .name(call.toolName())
                            .arguments(call.content())
                            .build());
                    index++;
                }
                mapped.add(AiMessage.from(calls));
                continue;
            }
            mapped.add(switch (message.role()) {
                case USER -> UserMessage.from(message.content());
                case ASSISTANT -> AiMessage.from(message.content() == null ? "" : message.content());
                case TOOL -> ToolExecutionResultMessage.from(message.toolCallId(), message.toolName(), message.content());
            });
            index++;
        }
        return mapped;
    }

    static ChatModel openRouter(String apiKey, String modelId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is required for the live profile");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(CHAT_BASE_URL)
                .modelName(modelId)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
