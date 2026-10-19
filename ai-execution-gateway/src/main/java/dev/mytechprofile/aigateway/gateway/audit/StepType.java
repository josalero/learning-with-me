package dev.mytechprofile.aigateway.gateway.audit;

/**
 * A {@code MODEL} step carries tokens and cost; a {@code TOOL} step carries the decision and HMACs.
 */
public enum StepType {
    MODEL,
    TOOL
}
