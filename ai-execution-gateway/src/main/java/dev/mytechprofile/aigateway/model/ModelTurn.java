package dev.mytechprofile.aigateway.model;

import java.util.List;

public record ModelTurn(List<ProposedToolCall> toolCalls, int inputTokens, int outputTokens) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
