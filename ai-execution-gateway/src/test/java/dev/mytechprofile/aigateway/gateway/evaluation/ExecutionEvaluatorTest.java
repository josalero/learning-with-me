package dev.mytechprofile.aigateway.gateway.evaluation;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEvaluatorTest {

    private final ExecutionEvaluator evaluator = new ExecutionEvaluator();

    @Test
    void budgetExhaustedIsNotEvaluated() {
        EvaluationResult result = evaluator.evaluate(new EvaluationInput(
                ExecutionStatus.BUDGET_EXHAUSTED, false, true, List.of("E-1"), List.of("E-1")));
        assertThat(result.verdict()).isEqualTo(Verdict.NOT_EVALUATED);
        assertThat(result.successful()).isFalse();
    }

    @Test
    void aDeniedToolIsAPolicyViolation() {
        EvaluationResult result = evaluator.evaluate(new EvaluationInput(
                ExecutionStatus.COMPLETED, true, true, List.of("E-1"), List.of("E-1")));
        assertThat(result.verdict()).isEqualTo(Verdict.POLICY_VIOLATION_ATTEMPTED);
        assertThat(result.successful()).isFalse();
    }

    @Test
    void citationsOutsideRetrievedEvidenceAreUnsupported() {
        EvaluationResult result = evaluator.evaluate(new EvaluationInput(
                ExecutionStatus.COMPLETED, false, true, List.of("E-9"), List.of("E-1")));
        assertThat(result.verdict()).isEqualTo(Verdict.UNSUPPORTED_CLAIM);
        assertThat(result.successful()).isFalse();
    }

    @Test
    void aMissingDraftIsUnsupported() {
        EvaluationResult result = evaluator.evaluate(
                new EvaluationInput(ExecutionStatus.COMPLETED, false, false, List.of(), List.of("E-1")));
        assertThat(result.verdict()).isEqualTo(Verdict.UNSUPPORTED_CLAIM);
    }

    @Test
    void groundedDraftPasses() {
        EvaluationResult result = evaluator.evaluate(new EvaluationInput(
                ExecutionStatus.RUNNING, false, true, List.of("E-1", "E-2"), List.of("E-1", "E-2", "E-3")));
        assertThat(result.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(result.successful()).isTrue();
    }
}
