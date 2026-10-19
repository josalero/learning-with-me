package dev.mytechprofile.aigateway.shared.api;

/**
 * Sums of the stored steps. Scenario 1 on the stub ends at 5 model calls, 4 tools, and 2,625 micro-USD.
 */
public record TotalsView(
        int modelCalls,
        int toolCallsRequested,
        int toolCallsPermitted,
        int toolCallsDenied,
        int inputTokens,
        int outputTokens,
        long costMicros) {}
