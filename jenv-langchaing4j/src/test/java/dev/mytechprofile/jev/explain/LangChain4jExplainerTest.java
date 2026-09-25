package dev.mytechprofile.jev.explain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.mytechprofile.jev.decide.Seniority;
import dev.mytechprofile.jev.decide.SeniorityDecision;

class LangChain4jExplainerTest {

    @Test
    void returnsTheChatModelTextWithoutChangingTheDecision() {
        ChatModel model = new FixedChatModel("Entrega con autonomía desde hace 4 años.");
        LangChain4jExplainer explainer = new LangChain4jExplainer(model, (resume, decision) -> "fallback");

        String explanation = explainer.explain(
                "4 years of experience.",
                new SeniorityDecision(Seniority.INTERMEDIATE, 0.88, Map.of(), "jev"));

        assertThat(explanation).isEqualTo("Entrega con autonomía desde hace 4 años.");
        assertThat(((FixedChatModel) model).prompt).contains("<resume>", "</resume>", "4 years of experience.");
    }

    @Test
    void fallsBackWhenTheChatModelThrows() {
        ChatModel model = new FixedChatModel(null);
        LangChain4jExplainer explainer = new LangChain4jExplainer(model, (resume, decision) -> "rubrica local");

        String explanation = explainer.explain(
                "4 years of experience.",
                new SeniorityDecision(Seniority.INTERMEDIATE, 0.88, Map.of(), "jev"));

        assertThat(explanation).isEqualTo("rubrica local");
    }

    private static final class FixedChatModel implements ChatModel {
        private final String text;
        private String prompt;

        private FixedChatModel(String text) {
            this.text = text;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            ChatMessage message = chatRequest.messages().getFirst();
            if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
                prompt = userMessage.singleText();
            }
            if (text == null) {
                throw new IllegalStateException("model down");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
        }
    }
}
