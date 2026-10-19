package dev.mytechprofile.aigateway.shared.api;

import dev.mytechprofile.aigateway.gateway.evaluation.Verdict;

/**
 * Verdict of a finished run. {@code successful} is true only for {@code PASSED}.
 */
public record EvaluationView(Verdict verdict, boolean successful) {}
