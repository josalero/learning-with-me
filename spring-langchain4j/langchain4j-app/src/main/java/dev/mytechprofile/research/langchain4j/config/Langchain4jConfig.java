package dev.mytechprofile.research.langchain4j.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Manual OpenRouter wiring via the OpenAI-compatible LangChain4j client.
 * Avoids the LangChain4j Spring Boot starter against Boot 4.1.
 */
@Configuration
public class Langchain4jConfig {

    @Bean
    @Primary
    ChatModel chatModel(ResearchProperties properties) {
        return openAiModel(properties, properties.chatModel());
    }

    @Bean
    @Qualifier("researchChatModel")
    ChatModel researchChatModel(ResearchProperties properties) {
        return openAiModel(properties, properties.researchModel());
    }

    private static OpenAiChatModel openAiModel(ResearchProperties properties, String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(properties.openrouter().apiKey())
                .baseUrl(properties.openrouter().baseUrl())
                .modelName(modelName)
                .temperature(properties.temperature())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
