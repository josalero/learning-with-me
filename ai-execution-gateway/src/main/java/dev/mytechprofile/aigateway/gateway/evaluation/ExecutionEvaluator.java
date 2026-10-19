package dev.mytechprofile.aigateway.gateway.evaluation;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;

/**
 * Chooses the verdict when a run closes. It never calls the model.
 */
@Component
public class ExecutionEvaluator {

    /**
     * First match wins: budget exhausted or failed is {@code NOT_EVALUATED}; any denied tool is
     * {@code POLICY_VIOLATION_ATTEMPTED}; no draft or citations outside the retrieved evidence is
     * {@code UNSUPPORTED_CLAIM}; otherwise {@code PASSED}.
     */
    public EvaluationResult evaluate(EvaluationInput input) {
        if (input.status() == ExecutionStatus.BUDGET_EXHAUSTED || input.status() == ExecutionStatus.FAILED) {
            return new EvaluationResult(Verdict.NOT_EVALUATED, false);
        }
        if (input.anyToolDenied()) {
            return new EvaluationResult(Verdict.POLICY_VIOLATION_ATTEMPTED, false);
        }
        if (!input.draftPresent()
                || !new HashSet<>(input.retrievedEvidenceIds()).containsAll(input.citedEvidenceIds())) {
            return new EvaluationResult(Verdict.UNSUPPORTED_CLAIM, false);
        }
        return new EvaluationResult(Verdict.PASSED, true);
    }
}
