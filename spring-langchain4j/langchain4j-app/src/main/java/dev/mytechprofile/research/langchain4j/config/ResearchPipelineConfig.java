package dev.mytechprofile.research.langchain4j.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.mytechprofile.research.langchain4j.agents.CriticAgent;
import dev.mytechprofile.research.langchain4j.agents.PlannerAgent;
import dev.mytechprofile.research.langchain4j.agents.ResearcherAgent;
import dev.mytechprofile.research.langchain4j.agents.WriterAgent;
import dev.mytechprofile.research.langchain4j.domain.Critique;

/**
 * Builds reusable agent graph pieces once (agents + writer/critic loop).
 * The outer sequence is assembled per request so a fresh step listener can be attached.
 */
@Configuration
public class ResearchPipelineConfig {

    @Bean
    PlannerAgent plannerAgent(ChatModel chatModel) {
        return AgenticServices.agentBuilder(PlannerAgent.class)
                .chatModel(chatModel)
                .name("planner")
                .systemMessage(PromptResources.load("planner.system.txt"))
                .outputKey("plan")
                .build();
    }

    @Bean
    ResearcherAgent researcherAgent(@Qualifier("researchChatModel") ChatModel researchChatModel) {
        return AgenticServices.agentBuilder(ResearcherAgent.class)
                .chatModel(researchChatModel)
                .name("researcher")
                .systemMessage(PromptResources.load("researcher.system.txt"))
                .outputKey("findingsDoc")
                .build();
    }

    @Bean
    WriterAgent writerAgent(ChatModel chatModel) {
        return AgenticServices.agentBuilder(WriterAgent.class)
                .chatModel(chatModel)
                .name("writer")
                .systemMessage(PromptResources.load("writer.system.txt"))
                .outputKey("draft")
                .build();
    }

    @Bean
    CriticAgent criticAgent(ChatModel chatModel) {
        return AgenticServices.agentBuilder(CriticAgent.class)
                .chatModel(chatModel)
                .name("critic")
                .systemMessage(PromptResources.load("critic.system.txt"))
                .outputKey("critique")
                .build();
    }

    @Bean
    @Qualifier("reviewLoop")
    UntypedAgent reviewLoop(WriterAgent writer, CriticAgent critic, ResearchProperties properties) {
        int maxIterations = properties.maxRevisions() + 1;
        int passThreshold = properties.passThreshold();
        return AgenticServices.loopBuilder()
                .subAgents(writer, critic)
                .maxIterations(maxIterations)
                .testExitAtLoopEnd(true)
                .exitCondition(scope -> {
                    Object critiqueState = scope.readState("critique");
                    if (critiqueState instanceof Critique critique) {
                        return critique.passes(passThreshold);
                    }
                    return false;
                })
                .build();
    }
}
