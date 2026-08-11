package dev.mytechprofile.research.springai.agents;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.Finding;
import dev.mytechprofile.research.springai.domain.ResearchPlan;

@Component
public class ChatClientResearcherAgent implements ResearcherAgent {

    private final ChatClient chatClient;

    public ChatClientResearcherAgent(@Qualifier("researchChatClient") ChatClient chatClient) {
        this.chatClient = chatClient.mutate()
                .defaultSystem(PromptResources.load("researcher.system.txt"))
                .build();
    }

    @Override
    public List<Finding> research(ResearchPlan plan) {
        List<Finding> findings = new ArrayList<>();
        for (String question : plan.questions()) {
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
            findings.add(new Finding(question, answer == null ? "" : answer.trim()));
        }
        return List.copyOf(findings);
    }
}
