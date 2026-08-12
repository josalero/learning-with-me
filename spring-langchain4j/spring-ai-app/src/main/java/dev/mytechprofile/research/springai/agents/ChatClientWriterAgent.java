package dev.mytechprofile.research.springai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.ResearchFindings;

@Component
public class ChatClientWriterAgent implements WriterAgent {

    private final ChatClient chatClient;

    public ChatClientWriterAgent(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient.mutate()
                .defaultSystem(PromptResources.load("writer.system.txt"))
                .build();
    }

    @Override
    public String write(String topic, ResearchFindings findingsDoc, Critique critique) {
        Critique notes = critique == null ? Critique.none() : critique;
        String draft = chatClient.prompt()
                .user(u -> u.text("""
                        Topic: {topic}
                        Findings: {findingsDoc}
                        Critique notes to address: {critique}
                        If critique notes exist, revise the prior draft accordingly.
                        Return only the markdown report.
                        """)
                        .param("topic", topic)
                        .param("findingsDoc", findingsDoc)
                        .param("critique", notes))
                .call()
                .content();
        if (draft == null || draft.isBlank()) {
            throw new IllegalStateException("Writer returned an empty draft");
        }
        return draft.trim();
    }
}
