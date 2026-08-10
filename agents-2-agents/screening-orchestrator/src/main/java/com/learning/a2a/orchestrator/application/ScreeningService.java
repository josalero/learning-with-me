package com.learning.a2a.orchestrator.application;

import com.learning.a2a.orchestrator.api.dto.ScreeningRequest;
import com.learning.a2a.orchestrator.api.dto.ScreeningResponse;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Screening use case: ask the orchestrator ChatClient to produce a verdict.
 *
 * <p>Delegation to remote A2A agents happens inside the LLM tool loop, not in this class.
 */
@Service
public class ScreeningService {

	private final ChatClient orchestratorChatClient;

	public ScreeningService(ChatClient orchestratorChatClient) {
		this.orchestratorChatClient = orchestratorChatClient;
	}

	public ScreeningResponse screen(ScreeningRequest request) {
		String verdict = orchestratorChatClient.prompt().user(request.toString()).call().content();
		if (verdict == null || verdict.isBlank()) {
			throw new IllegalStateException("Orchestrator returned an empty screening verdict");
		}
		return new ScreeningResponse(verdict);
	}
}
