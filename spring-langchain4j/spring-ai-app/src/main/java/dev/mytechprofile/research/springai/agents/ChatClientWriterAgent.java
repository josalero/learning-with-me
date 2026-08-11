package dev.mytechprofile.research.springai.agents;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.Critique;
import dev.mytechprofile.research.springai.domain.Finding;

@Component
public class ChatClientWriterAgent implements WriterAgent {

    private final ChatClient chatClient;

    public ChatClientWriterAgent(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient.mutate()
                .defaultSystem(PromptResources.load("writer.system.txt"))
                .build();
    }

    @Override
    public String write(String topic, List<Finding> findings, Critique previousCritique) {
        StringBuilder findingsBlock = new StringBuilder();
        for (Finding finding : findings) {
            findingsBlock.append("Q: ").append(finding.question()).append('\n')
                    .append("A: ").append(finding.answer()).append("\n\n");
        }
        Critique critique = previousCritique == null ? Critique.none() : previousCritique;
        String draft = chatClient.prompt()
                .user(u -> u.text("""
                        Topic: {topic}

                        Findings:
                        {findings}

                        Critique notes to address:
                        {critique}
                        """)
                        .param("topic", topic)
                        .param("findings", findingsBlock.toString())
                        .param("critique", critique.notes()))
                .call()
                .content();
        if (draft == null || draft.isBlank()) {
            throw new IllegalStateException("Writer returned an empty draft");
        }
        return draft.trim();
    }
}
