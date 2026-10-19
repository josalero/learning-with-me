package dev.mytechprofile.aigateway.evaluation;

import java.util.List;

import dev.mytechprofile.aigateway.domain.ExecutionStatus;

public record EvaluationInput(
        ExecutionStatus status,
        boolean anyToolDenied,
        boolean draftPresent,
        List<String> citedEvidenceIds,
        List<String> retrievedEvidenceIds) {}
