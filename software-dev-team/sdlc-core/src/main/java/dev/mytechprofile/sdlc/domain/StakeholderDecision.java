package dev.mytechprofile.sdlc.domain;

import java.util.List;
import java.util.Objects;

/**
 * Stakeholder decision to ship or send the feature back.
 *
 * <p>Sample: {@code StakeholderDecision.approved()} or REJECTED with follow-ups.
 */
public record StakeholderDecision(String decision, List<String> reasons, List<String> followUps) {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public StakeholderDecision {
        decision = Objects.requireNonNullElse(decision, REJECTED).trim().toUpperCase();
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        followUps = followUps == null
                ? List.of()
                : followUps.stream()
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .toList();
    }

    public static StakeholderDecision none() {
        return approved();
    }

    public static StakeholderDecision approved() {
        return new StakeholderDecision(APPROVED, List.of(), List.of());
    }

    public static StakeholderDecision rejected(String reason) {
        return new StakeholderDecision(REJECTED, List.of(reason), List.of());
    }

    /**
     * Returns true when the decision is {@link #APPROVED}.
     *
     * @return whether the stakeholder shipped the feature
     */
    public boolean isApproved() {
        return APPROVED.equals(decision);
    }

    /**
     * Returns true when another SDLC cycle would have nothing new to do.
     *
     * <p>Sample: {@code REJECTED} with empty {@code followUps} stops the stakeholder loop so the
     * Product Owner is not asked to rewrite the same brief. The run still escalates because
     * {@link #isApproved()} is false.
     *
     * @return whether {@code maxStakeholderCycles} should stop
     */
    public boolean endsCycle() {
        return isApproved() || followUps.isEmpty();
    }

    /**
     * Approves when QA already passed, and drops follow-ups that only ask to re-run tests that
     * executed.
     *
     * <p>Sample: QA PASS plus {@code REJECTED} / {@code followUps=["Ensure
     * UserControllerTest.createWithBlankNameReturns400 is executed"]} becomes {@link #approved()}
     * so the outer loop does not restart the whole SDLC.
     *
     * @param raw model or human decision
     * @param qa reconciled QA verdict
     * @param threshold {@code qaPassThreshold}
     * @param build executed tests from the gate
     * @param spec planned tests, used to recognize no-op follow-ups
     * @return {@link #approved()} or {@code raw} with no-op follow-ups removed
     */
    public static StakeholderDecision reconcile(
            StakeholderDecision raw, QaVerdict qa, int threshold, BuildResult build, AiSpec spec) {
        StakeholderDecision decision = raw == null ? rejected("pending") : raw;
        QaVerdict verdict = qa == null ? QaVerdict.none() : qa;
        BuildResult gate = build == null ? BuildResult.none() : build;
        AiSpec aiSpec = spec == null ? AiSpec.empty() : spec;
        if (verdict.passed(threshold)) {
            return approved();
        }
        List<String> remaining = decision.followUps().stream()
                .filter(item -> !isNoOpTestFollowUp(item, gate, aiSpec))
                .toList();
        if (remaining.isEmpty() && allPlannedTestsRan(aiSpec, gate)) {
            return approved();
        }
        return new StakeholderDecision(decision.decision(), decision.reasons(), remaining);
    }

    private static boolean allPlannedTestsRan(AiSpec spec, BuildResult build) {
        List<TraceabilityItem> items = spec.traceability();
        if (items.isEmpty() || !build.success()) {
            return false;
        }
        return items.stream().allMatch(item -> QaEvidence.ran(item.plannedTest(), build));
    }

    private static boolean isNoOpTestFollowUp(String followUp, BuildResult build, AiSpec spec) {
        String text = followUp.toLowerCase();
        boolean asksToRun = text.contains("test")
                && (text.contains("ensure")
                        || text.contains("execute")
                        || text.contains("executed")
                        || text.contains("run ")
                        || text.contains("passing"));
        if (!asksToRun) {
            return false;
        }
        for (String executed : build.executedTests()) {
            if (QaEvidence.appearsInText(QaEvidence.normalize(executed), followUp)) {
                return true;
            }
        }
        return spec.traceability().stream()
                .anyMatch(item -> QaEvidence.appearsInText(QaEvidence.normalize(item.plannedTest()), followUp)
                        && QaEvidence.ran(item.plannedTest(), build));
    }
}
