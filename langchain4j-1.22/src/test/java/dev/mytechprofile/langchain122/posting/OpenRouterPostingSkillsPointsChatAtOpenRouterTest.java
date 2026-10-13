package dev.mytechprofile.langchain122.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

class OpenRouterPostingSkillsPointsChatAtOpenRouterTest {

    @Test
    void chatClientUsesTheOpenRouterCatalogIdWithoutCallingTheApi() {
        ChatModel chat = OpenRouterPostingSkills.chatModel("test-key", OpenRouterPostingSkills.DEFAULT_CHAT_MODEL);

        assertEquals("https://openrouter.ai/api/v1", OpenRouterPostingSkills.CHAT_BASE_URL);
        OpenAiChatModel openAi = (OpenAiChatModel) chat;
        assertEquals("openai/gpt-4o-mini", openAi.defaultRequestParameters().modelName());
    }
}
