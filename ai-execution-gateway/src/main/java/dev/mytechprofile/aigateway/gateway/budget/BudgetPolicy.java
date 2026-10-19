package dev.mytechprofile.aigateway.gateway.budget;

import dev.mytechprofile.aigateway.gateway.delegation.DelegationGrantEntity;

/**
 * Limits for one execution, copied from its delegation grant.
 *
 * {@snippet :
 * BudgetPolicy policy = BudgetPolicy.from(grant);   // ats-nightly: 5 tools, 20 model calls, 8,000 tokens, 50,000 micro-USD
 * }
 */
public record BudgetPolicy(int maxToolCalls, int maxModelCalls, int maxTokens, long maxCostMicros) {

    /** The limits come from the delegation grant, so each delegator-agent pair has its own budget. */
    public static BudgetPolicy from(DelegationGrantEntity grant) {
        return new BudgetPolicy(
                grant.getMaxToolCalls(), grant.getMaxModelCalls(), grant.getMaxTokens(), grant.getMaxCostMicros());
    }
}
