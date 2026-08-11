package dev.mytechprofile.research.springai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.Critique;

@Component
public class ChatClientCriticAgent implements CriticAgent {

    private final ChatClient chatClient;

    public ChatClientCriticAgent(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient.mutate()
                .defaultSystem(PromptResources.load("critic.system.txt"))
                .build();
    }

    @Override
    public Critique critique(String draft) {
        Critique critique = chatClient.prompt()
                .user(u -> u.text("""
                        Draft to review:
                        {draft}
                        """)
                        .param("draft", draft))
                .call()
                .entity(Critique.class);
        if (critique == null) {
            throw new IllegalStateException("Critic returned no critique");
        }
        return critique;
    }
}
