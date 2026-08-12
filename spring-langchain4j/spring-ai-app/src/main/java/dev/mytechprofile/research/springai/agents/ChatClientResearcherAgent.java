package dev.mytechprofile.research.springai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.ResearchFindings;
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
    public ResearchFindings research(ResearchPlan plan) {
        ResearchFindings findingsDoc = chatClient.prompt()
                .user(u -> u.text("""
                        For each question in this plan, provide a concise factual answer.
                        Plan: {plan}
                        Return structured JSON with a findings array of objects that each have
                        question and answer fields.
                        """)
                        .param("plan", plan))
                .call()
                .entity(ResearchFindings.class);
        if (findingsDoc == null || findingsDoc.findings() == null || findingsDoc.findings().isEmpty()) {
            throw new IllegalStateException("Researcher returned no findings");
        }
        return findingsDoc;
    }
}
