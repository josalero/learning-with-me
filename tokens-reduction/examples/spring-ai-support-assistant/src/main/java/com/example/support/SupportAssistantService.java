package com.example.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Answers customer questions with retrieval-augmented generation. In the application this is a
 * {@code @Service}.
 *
 * <p>Every turn retrieves a large fixed number of chunks with no similarity threshold, then stuffs
 * all of them plus the full running transcript into a single user message. Conversation state lives
 * in an unbounded {@link List} that is replayed in full on each request, so cost grows without bound
 * across a session.
 */
public class SupportAssistantService {

	private final ChatClient assistant;
	private final VectorStore vectorStore;

	/** Append-only transcript with no window or summarization; replayed on every turn. */
	private final List<Message> conversation = new ArrayList<>();

	/**
	 * Creates the service with its collaborators (constructor injection).
	 *
	 * @param assistant configured chat client
	 * @param vectorStore retrieval store
	 */
	public SupportAssistantService(ChatClient assistant, VectorStore vectorStore) {
		this.assistant = assistant;
		this.vectorStore = vectorStore;
	}

	/**
	 * Answers a single customer question.
	 *
	 * @param question the customer's question
	 * @param customerId the customer identifier (for account-scoped retrieval)
	 * @return the assistant's answer
	 */
	public String answer(String question, String customerId) {
		List<Document> hits = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query(question)
						.topK(20)
						.build());

		StringBuilder context = new StringBuilder();
		if (hits != null) {
			for (Document hit : hits) {
				context.append(hit.getText()).append("\n\n");
			}
		}
		for (Message message : conversation) {
			context.append(message.getMessageType()).append(": ").append(message.getText()).append('\n');
		}

		String userMessage = "Customer " + customerId + " asks: " + question
				+ "\n\nUse the following context:\n" + context;

		String answer = assistant.prompt()
				.user(userMessage)
				.call()
				.content();

		conversation.add(new UserMessage(question));
		conversation.add(new AssistantMessage(answer == null ? "" : answer));
		return answer;
	}

	/**
	 * Returns an immutable copy of the running transcript.
	 *
	 * @return conversation messages so far
	 */
	public List<Message> transcript() {
		return List.copyOf(conversation);
	}
}
