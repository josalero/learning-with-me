package dev.mytechprofile.aigateway.execution;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Runs an execution on a bounded pool and keeps the caller's trace context.
 */
@Component
public class ExecutionDispatcher {

    private final ThreadPoolTaskExecutor executor;
    private final ExecutionOrchestrator orchestrator;

    public ExecutionDispatcher(ThreadPoolTaskExecutor executionExecutor, ExecutionOrchestrator orchestrator) {
        this.executor = executionExecutor;
        this.orchestrator = orchestrator;
    }

    public boolean hasCapacity() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return pool.getQueue().remainingCapacity() > 0 || pool.getActiveCount() < pool.getMaximumPoolSize();
    }

    public void dispatch(UUID executionId, Context context) {
        Context parent = context == null ? Context.current() : context;
        executor.execute(() -> {
            try (Scope ignored = parent.makeCurrent()) {
                orchestrator.run(executionId);
            }
        });
    }
}
