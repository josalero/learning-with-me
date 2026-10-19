package dev.mytechprofile.aigateway.gateway.evaluation;

import java.util.List;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;

/**
 * Facts the verdict needs: the status, whether any tool was denied, and the draft's citations versus the evidence actually retrieved.
 */
public record EvaluationInput(
        ExecutionStatus status,
        boolean anyToolDenied,
        boolean draftPresent,
        List<String> citedEvidenceIds,
        List<String> retrievedEvidenceIds) {}
