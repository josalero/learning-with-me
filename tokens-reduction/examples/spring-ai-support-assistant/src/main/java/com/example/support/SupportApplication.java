package com.example.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Wires the example by hand and runs a short multi-turn conversation that escalates.
 *
 * <p>The classes use the real Spring AI API. Only the model is swapped for an offline
 * {@link OfflineChatModel} test double so the demo runs without a provider API key; a production app
 * would use a Spring context and a real model starter instead.
 */
public final class SupportApplication {

	private SupportApplication() {
	}

	/**
	 * Runs the offline demonstration.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		ChatModel model = new OfflineChatModel();
		SupportAssistantConfig config = new SupportAssistantConfig();

		ChatClient assistantClient = config.supportAssistant(model, new SupportTools());
		VectorStore vectorStore = new InMemoryVectorStore();
		SupportAssistantService assistant = new SupportAssistantService(assistantClient, vectorStore);
		EscalationCoordinator coordinator =
				new EscalationCoordinator(assistant, config.specialistAgent(model));

		System.out.println("--- turn 1 (FAQ; still pays full system + tools + topK=20) ---");
		System.out.println(coordinator.handle("Hi, where is the status page?", "cust-123"));
		System.out.println();
		System.out.println("--- turn 2 (history grows; still unbounded) ---");
		System.out.println(coordinator.handle("Thanks. How do I get a refund?", "cust-123"));
		System.out.println();
		System.out.println("--- turn 3 (forces escalate → full transcript handoff) ---");
		System.out.println(coordinator.handle(
				"Please escalate this — the refund policy is unclear.",
				"cust-123"));
	}
}
