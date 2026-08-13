package dev.mytechprofile.sdlc.orchestration;

import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Blocks a pipeline until an operator posts an approval for a run.
 *
 * <p><strong>When to use:</strong> team {@code stakeholderMode: HUMAN}. The HITL agent calls
 * {@link #await(String, Duration)}; {@code POST /api/v1/runs/{id}/approve} calls {@link
 * #complete(String, StakeholderDecision)}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * gate.complete(runId, StakeholderDecision.approved());
 * }</pre>
 */
public final class HumanApprovalGate {

    private final ConcurrentHashMap<String, CompletableFuture<StakeholderDecision>> pending = new ConcurrentHashMap<>();

    /**
     * Waits for a human decision.
     *
     * @param runId run waiting for approval
     * @param timeout how long to wait
     * @return posted decision
     */
    public StakeholderDecision await(String runId, Duration timeout) {
        CompletableFuture<StakeholderDecision> future = new CompletableFuture<>();
        CompletableFuture<StakeholderDecision> existing = pending.putIfAbsent(runId, future);
        if (existing != null) {
            future = existing;
        }
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            pending.remove(runId, future);
            throw new IllegalStateException(
                    "Human approval timed out for run " + runId + " after " + timeout + ". Post POST /api/v1/runs/"
                            + runId + "/approve or raise sdlc.human-approval-timeout.",
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            pending.remove(runId, future);
            throw new IllegalStateException("Human approval interrupted for run " + runId, ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            pending.remove(runId, future);
            throw new IllegalStateException("Human approval failed for run " + runId, ex);
        }
    }

    /**
     * Completes a waiting run.
     *
     * @param runId run in {@code WAITING_APPROVAL}
     * @param decision operator decision
     * @return true when a waiter was waiting
     */
    public boolean complete(String runId, StakeholderDecision decision) {
        CompletableFuture<StakeholderDecision> future = pending.get(runId);
        if (future == null) {
            return false;
        }
        boolean completed = future.complete(decision);
        if (completed) {
            pending.remove(runId, future);
        }
        return completed;
    }

    /**
     * Returns whether {@code runId} is blocked on a human.
     *
     * @param runId run identifier
     * @return true when awaiting
     */
    public boolean isWaiting(String runId) {
        CompletableFuture<StakeholderDecision> future = pending.get(runId);
        return future != null && !future.isDone();
    }
}
