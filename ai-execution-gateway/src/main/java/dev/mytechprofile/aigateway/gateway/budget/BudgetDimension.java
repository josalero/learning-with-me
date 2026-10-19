package dev.mytechprofile.aigateway.gateway.budget;

/**
 * Which limit stopped the run.
 */
public enum BudgetDimension {
    TOKENS,
    MODEL_CALLS,
    TOOL_CALLS,
    COST
}
