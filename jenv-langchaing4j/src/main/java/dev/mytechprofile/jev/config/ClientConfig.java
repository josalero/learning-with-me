package dev.mytechprofile.jev.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.json.JsonMapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.mytechprofile.jev.decide.OpenRouterJevDecider;
import dev.mytechprofile.jev.decide.SeniorityDecider;
import dev.mytechprofile.jev.decide.YearsSeniorityDecider;
import dev.mytechprofile.jev.explain.LangChain4jExplainer;
import dev.mytechprofile.jev.explain.SeniorityExplainer;
import dev.mytechprofile.jev.explain.TemplateExplainer;

/**
 * Uses Jev when an OpenRouter key is present. Otherwise the year rubric runs locally.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenRouterProperties.class, AppProperties.class})
public final class ClientConfig {

    /**
     * Decisions client. Connect timeout is 5 seconds. Response timeout is
     * {@link OpenRouterJevDecider#RESPONSE_TIMEOUT}.
     *
     * @param properties supplies {@code baseUrl}
     * @return client whose requests are relative to the Decisions origin
     */
    @Bean
    WebClient openRouterWebClient(OpenRouterProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(OpenRouterJevDecider.RESPONSE_TIMEOUT);
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Selects the decider for this process.
     *
     * <p>A blank {@code openrouter.api-key} returns {@link YearsSeniorityDecider}.
     * A non-blank key returns {@link OpenRouterJevDecider} and does not call Jev
     * until {@code decide} runs.
     *
     * @param properties key, model, and decisions path
     * @param openRouterWebClient Decisions client
     * @param jsonMapper parser used only when live calls are enabled
     * @return the decider bean for {@code ResumeSeniorityService}
     */
    @Bean
    SeniorityDecider seniorityDecider(
            OpenRouterProperties properties,
            WebClient openRouterWebClient,
            ObjectProvider<JsonMapper> jsonMapper) {
        if (!properties.liveCallsEnabled()) {
            return new YearsSeniorityDecider();
        }
        return new OpenRouterJevDecider(
                properties,
                openRouterWebClient,
                jsonMapper.getObject(),
                OpenRouterJevDecider.RESPONSE_TIMEOUT);
    }

    /**
     * Chat client for explanations. Created only when a caller asks for the bean.
     *
     * <p>{@link #seniorityExplainer} asks only when the key is set and
     * {@code openrouter.explain} is true. Temperature is {@code 0.2} and
     * {@code maxTokens} is {@code 200}. Requests and responses are not logged.
     *
     * @param properties key, chat base URL, and chat model id
     * @return OpenRouter chat model
     */
    @Bean
    @Lazy
    ChatModel openRouterChatModel(OpenRouterProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.chatBaseUrl())
                .modelName(properties.chatModel())
                .temperature(0.2)
                .maxTokens(200)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Selects the explainer for this process.
     *
     * <p>Returns {@link TemplateExplainer} when the key is blank or
     * {@code openrouter.explain} is false. Otherwise returns
     * {@link LangChain4jExplainer} with the template as its fallback.
     *
     * @param properties key and explain flag
     * @param chatModels lazy chat client; not requested on the template path
     * @return explainer bean for {@code ResumeSeniorityService}
     */
    @Bean
    SeniorityExplainer seniorityExplainer(OpenRouterProperties properties, ObjectProvider<ChatModel> chatModels) {
        if (!properties.liveCallsEnabled() || !properties.explain()) {
            return new TemplateExplainer();
        }
        return new LangChain4jExplainer(chatModels.getObject(), new TemplateExplainer());
    }
}
