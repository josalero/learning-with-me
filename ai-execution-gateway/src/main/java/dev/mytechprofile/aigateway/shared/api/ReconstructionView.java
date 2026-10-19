package dev.mytechprofile.aigateway.shared.api;

import java.util.List;
import java.util.UUID;

import dev.mytechprofile.aigateway.gateway.execution.ExecutionStatus;

/**
 * Everything needed to explain a run afterward: who delegated to which agent, each step, and the totals.
 *
 * <p>Contains ids, decisions, counts, and HMACs only. No prompts, tool arguments, results, or candidate text.
 */
public record ReconstructionView(
        UUID executionId,
        String traceId,
        String agentId,
        DelegatorView delegator,
        ExecutionStatus status,
        List<StepView> steps,
        TotalsView totals,
        EvaluationView evaluation) {}
