package dev.mytechprofile.aigateway.budget;

import dev.mytechprofile.aigateway.persistence.DelegationGrantEntity;

public record BudgetPolicy(int maxToolCalls, int maxModelCalls, int maxTokens, long maxCostMicros) {

    public static BudgetPolicy from(DelegationGrantEntity grant) {
        return new BudgetPolicy(
                grant.getMaxToolCalls(), grant.getMaxModelCalls(), grant.getMaxTokens(), grant.getMaxCostMicros());
    }
}
