package dev.mytechprofile.statefulagent.agent;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.ChatModelSummarizer;
import org.springframework.ai.session.compaction.CompositeCompactionTrigger;
import org.springframework.ai.session.compaction.TokenCountTrigger;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import dev.mytechprofile.statefulagent.platform.AgentProperties;
import dev.mytechprofile.statefulagent.platform.ProviderUnavailableException;
import dev.mytechprofile.statefulagent.session.ModelProvider;
import dev.mytechprofile.statefulagent.tools.HiringTools;

@Component
public class ModelProviderRegistry {

    private final Map<ModelProvider, ChatClient> clients = new EnumMap<>(ModelProvider.class);
    private final StubChatModel stub;
    private final ForeignStubChatModel foreign;
    private final ObjectProvider<OpenAiChatModel> openRouter;
    private final ObjectProvider<DeepSeekChatModel> deepSeek;
    private final SessionService sessionService;
    private final HiringTools hiringTools;
    private final AgentProperties properties;
    private final ToolCallingManager toolCallingManager;

    public ModelProviderRegistry(StubChatModel stub, ForeignStubChatModel foreign, ObjectProvider<OpenAiChatModel> openRouter,
            ObjectProvider<DeepSeekChatModel> deepSeek, SessionService sessionService, HiringTools hiringTools,
            AgentProperties properties, ToolCallingManager toolCallingManager) {
        this.stub = stub;
        this.foreign = foreign;
        this.openRouter = openRouter;
        this.deepSeek = deepSeek;
        this.sessionService = sessionService;
        this.hiringTools = hiringTools;
        this.properties = properties;
        this.toolCallingManager = toolCallingManager;
    }

    public ChatClient client(ModelProvider provider) {
        return this.clients.computeIfAbsent(provider, this::build);
    }

    public ChatModel model(ModelProvider provider) {
        return switch (provider) {
            case STUB -> this.stub;
            case STUB_FOREIGN -> this.foreign;
            case OPENROUTER -> this.openRouter.getIfAvailable(() -> {
                throw new ProviderUnavailableException(provider.name());
            });
            case DEEPSEEK -> this.deepSeek.getIfAvailable(() -> {
                throw new ProviderUnavailableException(provider.name());
            });
        };
    }

    private ChatClient build(ModelProvider provider) {
        ChatModel chatModel = model(provider);
        AgentProperties.Compaction compaction = this.properties.getSession().getCompaction();
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a hiring assistant. Use tools for stages, scorecards, and offers. Never invent a recommendation, stage, or salary.")
                .defaultTools(this.hiringTools)
                .defaultAdvisors(SessionAdvisors.builder(this.sessionService)
                        .compaction(
                                CompositeCompactionTrigger.anyOf(
                                        new TurnCountTrigger(compaction.getMaxTurns()),
                                        TokenCountTrigger.builder().threshold(compaction.getMaxTokens()).build()),
                                WindowCompactionStrategy.builder()
                                        .lastTurns(compaction.getPreserveTurns())
                                        .summarizer(ChatModelSummarizer.builder(chatModel)
                                                .shadowPrompt("Summarize the conversation.")
                                                .build())
                                        .build())
                        .asyncCompaction(false)
                        .build())
                .build();
    }

    public ToolCallingManager toolCallingManager() {
        return this.toolCallingManager;
    }
}
