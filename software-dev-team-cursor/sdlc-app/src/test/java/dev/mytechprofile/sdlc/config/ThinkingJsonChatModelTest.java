package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkingJsonChatModelTest {

    @Test
    void doChat_whenContentIsNullAndThinkingHasJson_copiesJsonIntoText() {
        ChatModel inner = new StubChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(null)
                        .thinking("{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}")
                        .build())
                .build());
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Return JSON for ReviewVerdict"))
                .build();

        String text = new ThinkingJsonChatModel(inner).chat(request).aiMessage().text();

        assertThat(text).contains("\"APPROVE\"").contains("blockingCount");
    }

    @Test
    void doChat_whenContentAlreadyHasJson_leavesText() {
        ChatModel inner = new StubChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"decision\":\"REQUEST_CHANGES\",\"findings\":[],\"blockingCount\":1}"))
                .build());
        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("review")).build();

        String text = new ThinkingJsonChatModel(inner).chat(request).aiMessage().text();

        assertThat(text).contains("REQUEST_CHANGES");
    }

    @Test
    void recover_whenToolCallsPresent_doesNotRewriteNullText() {
        AiMessage tools = AiMessage.builder()
                .text(null)
                .thinking("{\"decision\":\"APPROVE\"}")
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("1")
                        .name("readFile")
                        .arguments("{}")
                        .build()))
                .build();
        ChatResponse response = ChatResponse.builder().aiMessage(tools).build();

        ChatResponse recovered = ThinkingJsonChatModel.recover(response);

        assertThat(recovered.aiMessage().text()).isNull();
        assertThat(recovered.aiMessage().hasToolExecutionRequests()).isTrue();
    }

    @Test
    void recover_whenContentAndThinkingAreEmpty_usesFallbackReviewVerdict() {
        ChatResponse empty = ChatResponse.builder()
                .aiMessage(AiMessage.builder().text(null).thinking(null).build())
                .build();

        ChatResponse recovered =
                ThinkingJsonChatModel.recover(empty, "{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}");

        assertThat(recovered.aiMessage().text()).contains("\"APPROVE\"").contains("findings");
    }

    @Test
    void doChat_whenContentAndThinkingAreEmpty_usesConstructorFallback() {
        ChatModel inner = new StubChatModel(ChatResponse.builder()
                .aiMessage(AiMessage.builder().text(null).thinking("").build())
                .build());
        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("review")).build();

        String text = new ThinkingJsonChatModel(inner, "{\"decision\":\"APPROVE\",\"findings\":[],\"blockingCount\":0}")
                .chat(request)
                .aiMessage()
                .text();

        assertThat(text).contains("\"APPROVE\"");
    }

    private static final class StubChatModel implements ChatModel {
        private final ChatResponse response;

        private StubChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return response;
        }
    }
}
