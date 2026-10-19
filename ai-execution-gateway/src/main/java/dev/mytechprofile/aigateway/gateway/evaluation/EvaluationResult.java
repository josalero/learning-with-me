package dev.mytechprofile.aigateway.gateway.evaluation;

/**
 * Verdict plus {@code successful}, which is true only for {@code PASSED}.
 */
public record EvaluationResult(Verdict verdict, boolean successful) {}
