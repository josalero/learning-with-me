package dev.mytechprofile.sdlc.catalog;

import dev.mytechprofile.sdlc.domain.StakeholderMode;

/**
 * Bounded loop limits for a team.
 *
 * <p>Sample: max 3 implementation attempts, a QA pass threshold of 80, and 25 developer tool
 * calls per turn.
 */
public record TeamPolicy(
        int maxSpecRework,
        int maxImplementationAttempts,
        int maxReviewCycles,
        int maxQaCycles,
        int maxStakeholderCycles,
        int qaPassThreshold,
        StakeholderMode stakeholderMode,
        int maxDeveloperToolCalls,
        int maxReadOnlyToolCalls) {

    public TeamPolicy {
        if (maxSpecRework < 1
                || maxImplementationAttempts < 1
                || maxReviewCycles < 1
                || maxQaCycles < 1
                || maxStakeholderCycles < 1) {
            throw new IllegalArgumentException("all loop maxima must be at least 1");
        }
        if (qaPassThreshold < 0 || qaPassThreshold > 100) {
            throw new IllegalArgumentException("qaPassThreshold must be 0..100");
        }
        if (stakeholderMode == null) {
            stakeholderMode = StakeholderMode.AGENT;
        }
        if (maxDeveloperToolCalls < 1) {
            maxDeveloperToolCalls = 25;
        }
        if (maxReadOnlyToolCalls < 1) {
            maxReadOnlyToolCalls = 10;
        }
    }
}
