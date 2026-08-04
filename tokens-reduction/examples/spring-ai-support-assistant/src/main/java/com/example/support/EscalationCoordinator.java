package com.example.support;

import java.util.stream.Collectors;

/**
 * Decides when a case needs a specialist and performs the handoff. In the application this is a
 * {@code @Service}.
 *
 * <p>When escalating, it forwards the <em>entire</em> transcript to the specialist rather than a
 * compact, task-scoped brief. The specialist therefore re-ingests the full front-line context,
 * duplicating tokens that were already spent.
 */
public class EscalationCoordinator {

	private final SupportAssistantService assistant;
	private final SpecialistAgent specialist;

	/**
	 * Creates the coordinator.
	 *
	 * @param assistant the front-line assistant
	 * @param specialist the escalation specialist
	 */
	public EscalationCoordinator(SupportAssistantService assistant, SpecialistAgent specialist) {
		this.assistant = assistant;
		this.specialist = specialist;
	}

	/**
	 * Handles a question, escalating to the specialist when the front-line answer is not sufficient.
	 *
	 * @param question the customer's question
	 * @param customerId the customer identifier
	 * @return the final answer
	 */
	public String handle(String question, String customerId) {
		String draft = assistant.answer(question, customerId);
		if (!needsSpecialist(question, draft)) {
			return draft;
		}

		String entireConversation = assistant.transcript().stream()
				.map(message -> message.getMessageType() + ": " + message.getText())
				.collect(Collectors.joining("\n"));

		return specialist.handoff(entireConversation);
	}

	private boolean needsSpecialist(String question, String draft) {
		String haystack = (question + "\n" + draft).toLowerCase();
		return haystack.contains("escalate");
	}
}
