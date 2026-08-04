package com.example.support;

import org.springframework.ai.chat.client.ChatClient;

/**
 * A second agent that resolves complex cases the front-line assistant escalates. In the application
 * this is a {@code @Service}.
 */
public class SpecialistAgent {

	private final ChatClient specialistClient;

	/**
	 * Creates the specialist agent.
	 *
	 * @param specialistClient the specialist's chat client
	 */
	public SpecialistAgent(ChatClient specialistClient) {
		this.specialistClient = specialistClient;
	}

	/**
	 * Resolves an escalated case from a handoff payload.
	 *
	 * @param handoffPayload the material provided by the coordinator
	 * @return the specialist's resolution
	 */
	public String handoff(String handoffPayload) {
		return specialistClient.prompt()
				.user(handoffPayload)
				.call()
				.content();
	}
}
