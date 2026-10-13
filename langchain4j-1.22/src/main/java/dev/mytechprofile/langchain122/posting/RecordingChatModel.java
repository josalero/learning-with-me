package dev.mytechprofile.langchain122.posting;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.mytechprofile.langchain122.sample.AtsSample;

/**
 * Keeps the last request so a test can count how many times the posting text was attached.
 */
public final class RecordingChatModel implements ChatModel {

    private ChatRequest lastRequest;

    public ChatRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        this.lastRequest = chatRequest;
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(AtsSample.EXTRACTED_SKILLS))
                .build();
    }
}
