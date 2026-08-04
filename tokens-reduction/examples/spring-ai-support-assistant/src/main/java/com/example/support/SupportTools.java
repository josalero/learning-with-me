package com.example.support;

import org.springframework.ai.tool.annotation.Tool;

/**
 * The support assistant's tool bean. In the application this is a {@code @Component} whose
 * {@code @Tool} methods are all registered with the model through
 * {@link SupportAssistantConfig#supportAssistant}.
 *
 * <p>This class grew organically: every time a team needed the assistant to "also do X", another
 * {@code @Tool} method was added here. The result is a broad, privileged tool surface attached to a
 * single general-purpose assistant — including destructive operations that ordinary support chats
 * should never be able to trigger.
 */
public class SupportTools {

	/**
	 * Searches the product knowledge base.
	 *
	 * @param query natural-language query
	 * @return a knowledge-base snippet
	 */
	@Tool(description = "Search the product knowledge base for help articles and policies.")
	public String searchKnowledgeBase(String query) {
		return "kb: " + query;
	}

	/**
	 * Looks up a customer account summary.
	 *
	 * @param customerId customer identifier
	 * @return account summary
	 */
	@Tool(description = "Look up a customer account summary by id.")
	public String lookupAccount(String customerId) {
		return "account: " + customerId;
	}

	/**
	 * Issues a refund for an order.
	 *
	 * @param orderId order identifier
	 * @param amount refund amount
	 * @return confirmation text
	 */
	@Tool(description = "Issue a refund for an order.")
	public String issueRefund(String orderId, String amount) {
		return "refunded " + amount + " for " + orderId;
	}

	/**
	 * Deletes a customer account. Privileged and irreversible.
	 *
	 * @param customerId customer identifier
	 * @return confirmation text
	 */
	@Tool(description = "Permanently delete a customer account and all associated data.")
	public String deleteAccount(String customerId) {
		return "deleted " + customerId;
	}

	/**
	 * Exports the full audit log for an organization. Privileged.
	 *
	 * @param organizationId organization identifier
	 * @return export location
	 */
	@Tool(description = "Export the complete audit log for an organization.")
	public String exportAuditLog(String organizationId) {
		return "audit-log://" + organizationId;
	}

	/**
	 * Changes the owner of an organization. Privileged.
	 *
	 * @param organizationId organization identifier
	 * @param newOwnerId new owner identifier
	 * @return confirmation text
	 */
	@Tool(description = "Transfer ownership of an organization to another user.")
	public String changeOrganizationOwner(String organizationId, String newOwnerId) {
		return "owner of " + organizationId + " -> " + newOwnerId;
	}

	/**
	 * Runs a usage report for an organization.
	 *
	 * @param organizationId organization identifier
	 * @return report summary
	 */
	@Tool(description = "Run a usage report for an organization.")
	public String runUsageReport(String organizationId) {
		return "usage-report: " + organizationId;
	}
}
