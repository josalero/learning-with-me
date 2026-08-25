package dev.mytechprofile.sdlc.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

class ScriptedChatModelTest {

    @Test
    void doChat_whenPromptAsksForChangeSummary_doesNotReturnAiSpecJson() {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from(
                                """
                        AI spec:
                        {"summary":"x","filesToChange":[],"traceability":[]}

                        Return JSON for ChangeSummary with filesTouched, rationale, and notes.
                        """))
                .build();

        String text = ScriptedChatModel.INSTANCE.chat(request).aiMessage().text();

        assertThat(text).contains("filesTouched").doesNotContain("\"summary\"");
    }
}
