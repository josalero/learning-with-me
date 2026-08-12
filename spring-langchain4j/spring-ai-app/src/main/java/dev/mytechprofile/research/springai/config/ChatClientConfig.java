package dev.mytechprofile.research.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Pre-built ChatClients so agents stay free of OpenAI option types.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @Primary
    @Qualifier("chatClient")
    ChatClient chatClient(ChatModel chatModel, ResearchProperties properties) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .maxTokens(properties.maxTokens()))
                .build();
    }

    @Bean
    @Qualifier("researchChatClient")
    ChatClient researchChatClient(ChatModel chatModel, ResearchProperties properties) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.researchModel())
                        .maxTokens(properties.maxTokens()))
                .build();
    }
}
