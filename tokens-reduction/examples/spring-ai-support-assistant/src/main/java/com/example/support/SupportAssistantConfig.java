package com.example.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Wires the support assistant. In the application this is a {@code @Configuration} class and each
 * factory method is a {@code @Bean}.
 *
 * <p>Two ordinary-looking decisions here are expensive on every single request: a large evergreen
 * system prompt is applied with {@code defaultSystem(...)}, and the entire {@link SupportTools} bean
 * (all of its {@code @Tool} methods) is attached with {@code defaultTools(...)}.
 */
public class SupportAssistantConfig {

	/**
	 * The always-on system prompt. It accumulated instructions for billing, technical, and account
	 * scenarios over time, so it is now sent in full on every turn — even for a simple greeting.
	 */
	private static final String SUPPORT_SYSTEM_PROMPT = """
			You are the customer-support assistant for every product line, region, plan, and
			internal department. Restate the customer's question before answering, reproduce the
			relevant policy in full, and explain each decision in detail with background context.

			For billing questions, consider invoices, payment methods, refunds, disputes, credits,
			taxes, currency and exchange rates, renewal dates, plan migrations, proration, and every
			documented exception. For technical questions, consider authentication, SSO, networking,
			browser and mobile compatibility, data retention, backups, integrations, webhooks, rate
			limits, API versioning, and recent deployment history. For account questions, consider
			roles and permissions, audit logs, organization settings, seat management, user
			invitations, security policies, compliance obligations, and administrator procedures.

			Always prefer thoroughness over brevity. Include the full text of any policy you cite,
			list the tools you considered, summarize every retrieved document, and add an appendix
			with related articles. Never omit a section from the supplied documentation. These
			instructions apply equally to greetings, status checks, password resets, and every other
			interaction, regardless of how simple the request appears to be.
			""";

	/**
	 * Builds the general-purpose assistant bean.
	 *
	 * @param model provider chat model
	 * @param tools the shared support tool bean
	 * @return configured chat client
	 */
	public ChatClient supportAssistant(ChatModel model, SupportTools tools) {
		return ChatClient.builder(model)
				.defaultSystem(SUPPORT_SYSTEM_PROMPT)
				.defaultTools(tools)
				.build();
	}

	/**
	 * Builds the specialist agent bean used for escalations.
	 *
	 * @param model provider chat model
	 * @return specialist agent
	 */
	public SpecialistAgent specialistAgent(ChatModel model) {
		ChatClient specialistClient = ChatClient.builder(model)
				.defaultSystem("You are a senior support specialist. Resolve the escalated case.")
				.build();
		return new SpecialistAgent(specialistClient);
	}
}
