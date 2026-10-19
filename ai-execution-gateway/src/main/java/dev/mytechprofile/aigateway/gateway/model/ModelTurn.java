package dev.mytechprofile.aigateway.gateway.model;

import java.util.List;

/**
 * What the model answered in one turn: the proposed tool calls and the tokens it used.
 */
public record ModelTurn(List<ProposedToolCall> toolCalls, int inputTokens, int outputTokens) {
    /** False means the model is done and the loop ends {@code COMPLETED}. */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
