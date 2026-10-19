package dev.mytechprofile.aigateway.gateway.execution;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Runs an execution on the bounded {@code executionExecutor} pool and keeps the caller's trace context,
 * so the worker's spans join the trace started by the HTTP request.
 */
@Component
public class ExecutionDispatcher {

    private final ThreadPoolTaskExecutor executor;
    private final ExecutionOrchestrator orchestrator;

    public ExecutionDispatcher(ThreadPoolTaskExecutor executionExecutor, ExecutionOrchestrator orchestrator) {
        this.executor = executionExecutor;
        this.orchestrator = orchestrator;
    }

    /** A hint only: another request can take the last slot before {@link #dispatch} runs. */
    public boolean hasCapacity() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getQueue().remainingCapacity() > 0 || pool.getActiveCount() < pool.getMaximumPoolSize();
    }

    /**
     * Runs the execution on the bounded pool under the caller's trace context.
     *
     * @throws java.util.concurrent.RejectedExecutionException when the pool and queue are full
     */
    public void dispatch(UUID executionId, Context context) {
        Context parent = context == null ? Context.current() : context;
        executor.execute(() -> {
            try (Scope ignored = parent.makeCurrent()) {
                orchestrator.run(executionId);
            }
        });
    }
}
