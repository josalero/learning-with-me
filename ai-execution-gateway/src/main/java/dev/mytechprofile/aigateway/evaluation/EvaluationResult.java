package dev.mytechprofile.aigateway.evaluation;

import dev.mytechprofile.aigateway.domain.Verdict;

public record EvaluationResult(Verdict verdict, boolean successful) {}
