package dev.mytechprofile.aigateway.evaluation;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import dev.mytechprofile.aigateway.domain.ExecutionStatus;
import dev.mytechprofile.aigateway.domain.Verdict;

@Component
public class ExecutionEvaluator {

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
