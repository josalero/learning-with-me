package com.learning.a2a.skills.config;

import java.util.List;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.springaicommunity.a2a.server.executor.DefaultAgentExecutor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A2A server beans: Agent Card discovery metadata and executor bridge to ChatClient.
 */
@Configuration
public class SkillsA2aConfiguration {

	@Bean
	AgentExecutor agentExecutor(ChatClient skillsChatClient) {
		return new DefaultAgentExecutor(skillsChatClient, (client, requestContext) -> {
			String userMessage = DefaultAgentExecutor.extractTextFromMessage(requestContext.getMessage());
			return client.prompt().user(userMessage).call().content();
		});
	}

	@Bean
	AgentCard agentCard(SkillsAgentProperties properties) {
		return new AgentCard.Builder()
			.name("Skills Matcher Agent")
			.description("Evaluates how well a candidate's skills match a job's required skills")
			.url(properties.normalizedPublicUrl())
			.version("1.0.0")
			.capabilities(new AgentCapabilities.Builder().streaming(false).build())
			.defaultInputModes(List.of("text"))
			.defaultOutputModes(List.of("text"))
			.skills(List.of(new AgentSkill.Builder()
				.id("skills_matching")
				.name("Skills Matching")
				.description("Compares candidate skills to job requirements and scores the fit")
				.tags(List.of("hiring", "recruiting"))
				.build()))
			.protocolVersion("0.3.0")
			.build();
	}
}
