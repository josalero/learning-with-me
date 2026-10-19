package dev.mytechprofile.aigateway.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.mytechprofile.aigateway.persistence.ModelPricingEntity;
import dev.mytechprofile.aigateway.persistence.ModelPricingRepository;

class LangChain4jModelGatewayTest {

    @Test
    void returnsProposedToolCallsWithoutExecutingThem() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("search_evidence")
                        .arguments("{\"candidateId\":\"C-101\"}")
                        .build())))
                .tokenUsage(new TokenUsage(12, 4))
                .build());
        ModelPricingRepository pricing = mock(ModelPricingRepository.class);
        LangChain4jModelGateway gateway = new LangChain4jModelGateway(chatModel, "live-model", 256, pricing, 150, 600);

        ModelTurn turn = gateway.complete("C-101", List.of(ConversationMessage.user("Evaluate candidate C-101.")));

        assertThat(turn.toolCalls()).containsExactly(new ProposedToolCall("call-1", "search_evidence", "{\"candidateId\":\"C-101\"}"));
        assertThat(turn.inputTokens()).isEqualTo(12);
        assertThat(turn.outputTokens()).isEqualTo(4);
        ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(request.capture());
        assertThat(request.getValue().toolSpecifications()).isNotEmpty();
    }

    @Test
    void groupsConsecutiveToolCallsIntoOneAssistantMessage() {
        var mapped = LangChain4jModelGateway.toChatMessages(List.of(
                ConversationMessage.user("Evaluate"),
                ConversationMessage.assistantTool("a", "search_evidence", "{}"),
                ConversationMessage.assistantTool("b", "get_candidate_summary", "{}"),
                ConversationMessage.toolResult("a", "search_evidence", "{\"items\":[]}")));
        assertThat(mapped).hasSize(3);
        assertThat(mapped.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) mapped.get(1)).toolExecutionRequests()).hasSize(2);
    }

    @Test
    void blankApiKeyIsRejected() {
        assertThatThrownBy(() -> LangChain4jModelGateway.openRouter(" ", "some-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENROUTER_API_KEY");
    }

    @Test
    void buildsAnOpenRouterClientWithoutLoggingPayloads() {
        var model = LangChain4jModelGateway.openRouter("test-key", "some-model");
        assertThat(model).isNotNull();
    }

    @Test
    void upsertsLivePricing() {
        ModelPricingRepository pricing = mock(ModelPricingRepository.class);
        when(pricing.findById("live-model")).thenReturn(java.util.Optional.empty());
        LangChain4jModelGateway gateway =
                new LangChain4jModelGateway(mock(ChatModel.class), "live-model", 256, pricing, 150, 600);

        gateway.requireLiveSettings();

        ArgumentCaptor<ModelPricingEntity> saved = ArgumentCaptor.forClass(ModelPricingEntity.class);
        verify(pricing).save(saved.capture());
        assertThat(saved.getValue().getModelId()).isEqualTo("live-model");
        assertThat(saved.getValue().getInputMicrosPer1k()).isEqualTo(150);
        assertThat(gateway.estimatedInputTokens(List.of(ConversationMessage.user("abcd")))).isEqualTo(1);
    }
}
