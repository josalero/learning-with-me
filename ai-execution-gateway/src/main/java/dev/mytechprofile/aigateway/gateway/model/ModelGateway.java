package dev.mytechprofile.aigateway.gateway.model;

import java.util.List;

/**
 * Provider port. Implementations return proposed tool calls and token usage; they never execute tools.
 *
 * {@snippet :
 * List<ConversationMessage> messages = new ArrayList<>(List.of(ConversationMessage.user("Evaluate candidate C-101")));
 * ModelTurn turn = models.complete("C-101", messages);
 * turn.toolCalls();   // e.g. [ProposedToolCall("call-1", "get_candidate_summary", "{\"candidateId\":\"C-101\"}")]
 * }
 */
public interface ModelGateway {

    /** Model id used for pricing; it must have a {@code model_pricing} row. */
    String modelId();

    /** Input tokens to reserve before the call. Must not underestimate on the stub, where it is exact. */
    int estimatedInputTokens(List<ConversationMessage> messages);

    /** Output cap sent to the provider and reserved before each call. */
    int maxOutputTokens();

    /**
     * Asks the model for its next move. Returns proposals and usage; must not run any tool.
     *
     * @param candidateId lets the stub pick its script; the live adapter ignores it
     */
    ModelTurn complete(String candidateId, List<ConversationMessage> messages);
}
