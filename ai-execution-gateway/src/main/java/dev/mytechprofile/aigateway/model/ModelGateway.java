package dev.mytechprofile.aigateway.model;

import java.util.List;

/**
 * Provider port. Implementations return proposed tool calls. They do not execute tools.
 */
public interface ModelGateway {

    String modelId();

    int estimatedInputTokens(List<ConversationMessage> messages);

    int maxOutputTokens();

    ModelTurn complete(String candidateId, List<ConversationMessage> messages);
}
