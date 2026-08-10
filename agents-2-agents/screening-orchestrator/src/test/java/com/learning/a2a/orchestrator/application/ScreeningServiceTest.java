package com.learning.a2a.orchestrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learning.a2a.orchestrator.api.dto.ScreeningRequest;
import com.learning.a2a.orchestrator.api.dto.ScreeningResponse;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class ScreeningServiceTest {

	@Test
	void screen_whenChatClientReturnsContent_wrapsVerdict() {
		ChatClient chatClient = mockChatClient("Strong match overall.");
		ScreeningService service = new ScreeningService(chatClient);

		ScreeningResponse response = service.screen(sampleRequest());

		assertThat(response.verdict()).isEqualTo("Strong match overall.");
	}

	@Test
	void screen_whenChatClientReturnsBlank_throwsIllegalState() {
		ChatClient chatClient = mockChatClient("  ");
		ScreeningService service = new ScreeningService(chatClient);

		assertThatThrownBy(() -> service.screen(sampleRequest()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("empty screening verdict");
	}

	private static ScreeningRequest sampleRequest() {
		return new ScreeningRequest(
				"Jane Doe",
				"jane@example.com",
				"Backend Developer",
				"Java, Spring Boot",
				"Java, Spring Boot",
				100_000);
	}

	@SuppressWarnings("unchecked")
	private static ChatClient mockChatClient(String content) {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt()).thenReturn(requestSpec);
		when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn(content);
		return chatClient;
	}
}
