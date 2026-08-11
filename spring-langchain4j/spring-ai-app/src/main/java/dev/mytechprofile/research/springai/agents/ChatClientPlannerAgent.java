package dev.mytechprofile.research.springai.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.mytechprofile.research.springai.config.PromptResources;
import dev.mytechprofile.research.springai.domain.ResearchPlan;

@Component
public class ChatClientPlannerAgent implements PlannerAgent {

    private final ChatClient chatClient;

    public ChatClientPlannerAgent(@Qualifier("chatClient") ChatClient chatClient) {
        this.chatClient = chatClient.mutate()
                .defaultSystem(PromptResources.load("planner.system.txt"))
                .build();
    }

    @Override
    public ResearchPlan plan(String topic, int depth) {
        ResearchPlan plan = chatClient.prompt()
                .user(u -> u.text("""
                        Topic: {topic}
                        Depth: {depth}
                        Produce {depth} research questions.
                        """)
                        .param("topic", topic)
                        .param("depth", depth))
                .call()
                .entity(ResearchPlan.class);
        if (plan == null || plan.questions() == null || plan.questions().isEmpty()) {
            throw new IllegalStateException("Planner returned an empty plan");
        }
        return plan;
    }
}
