package com.example.support;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline {@link ChatModel} test double so the example runs deterministically without a provider
 * API key or network access.
 *
 * <p>It returns a fixed acknowledgement and never issues tool calls. In a production application you
 * would remove this class and add a real model starter (for example
 * {@code spring-ai-starter-model-openai}) so Spring Boot auto-configures a {@link ChatModel} bean.
 */
public class OfflineChatModel implements ChatModel {

	@Override
	public ChatResponse call(Prompt prompt) {
		AssistantMessage reply = new AssistantMessage(
				"[offline demo] request received; no provider is configured.");
		return new ChatResponse(List.of(new Generation(reply)));
	}
}
