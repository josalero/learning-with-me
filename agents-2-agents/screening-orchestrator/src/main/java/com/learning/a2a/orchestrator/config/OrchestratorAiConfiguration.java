package com.learning.a2a.orchestrator.config;

import com.learning.a2a.orchestrator.a2a.AgentRegistry;
import com.learning.a2a.orchestrator.a2a.tools.RemoteAgentTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Orchestrator ChatClient: system prompt lists discovered agents; tools enable A2A delegation.
 */
@Configuration
public class OrchestratorAiConfiguration {

	@Bean
	ChatClient orchestratorChatClient(
			ChatClient.Builder chatClientBuilder,
			AgentRegistry agentRegistry,
			RemoteAgentTools remoteAgentTools) {
		return chatClientBuilder
			.defaultSystem("""
					You are a job-screening orchestrator for recruiters.
					You do not evaluate candidates yourself. Instead, you delegate
					to the following remote agents:

					%s

					Use send-message-to-agent with the exact agent name shown above.
					Ask the Skills Matcher Agent to compare requiredSkills vs candidateSkills.
					Then return one short screening summary for the recruiter.
					""".formatted(agentRegistry.describeAgents()))
			.defaultTools(remoteAgentTools)
			.build();
	}
}
