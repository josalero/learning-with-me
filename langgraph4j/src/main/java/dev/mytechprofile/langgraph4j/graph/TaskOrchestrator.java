package dev.mytechprofile.langgraph4j.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

/**
 * Starts and resumes implementation threads against the compiled graph.
 */
public final class TaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TaskOrchestrator.class);

    private final CompiledGraph<TaskState> graph;
    private final ConcurrentHashMap<String, RunnableConfig> threads = new ConcurrentHashMap<>();

    public TaskOrchestrator(CompiledGraph<TaskState> graph) {
        this.graph = graph;
    }

    public static TaskOrchestrator create(
            CompiledGraph<TaskState> graph,
            MemorySaver unusedSaver) {
        return new TaskOrchestrator(graph);
    }

    /**
     * Runs {@code plan} and interrupts after that node, before {@code approve}.
     *
     * <pre>{@code
     * TaskView view = orchestrator.start("Return ok from PingService", "", "main");
     * }</pre>
     */
    public TaskView start(String brief, String repoUrl, String startingRef) {
        String threadId = UUID.randomUUID().toString();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(TaskState.THREAD_ID, threadId);
        input.put(TaskState.BRIEF, brief == null ? "" : brief);
        input.put(TaskState.REPO_URL, repoUrl == null ? "" : repoUrl);
        input.put(TaskState.STARTING_REF, startingRef == null || startingRef.isBlank() ? "main" : startingRef);
        run(GraphInput.args(input), config);
        threads.put(threadId, threadConfig(threadId));
        log.info("Started implementation thread");
        return get(threadId);
    }

    /**
     * Applies the human decision and resumes from the interrupt.
     *
     * <pre>{@code
     * orchestrator.resume(id, Decision.APPROVE, null);
     * }</pre>
     */
    public TaskView resume(String threadId, Decision decision, String promptOverride) {
        RunnableConfig config = requireThread(threadId);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(TaskState.DECISION, decision.name());
        if (promptOverride != null && !promptOverride.isBlank()) {
            values.put(TaskState.PROMPT, promptOverride.trim());
        }
        try {
            // Merge the decision, resume, then read the latest checkpoint for this thread.
            // updateState returns a pinned checkpoint id; getState on that pin would hide later nodes.
            RunnableConfig updated = graph.updateState(config, values);
            run(GraphInput.resume(), updated);
            threads.put(threadId, threadConfig(threadId));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not resume implementation thread", ex);
        }
        return get(threadId);
    }

    public TaskView get(String threadId) {
        RunnableConfig config = requireThread(threadId);
        TaskState state = graph.getState(config).state();
        return TaskView.from(threadId, state);
    }

    public String mermaid() {
        return graph.getGraph(GraphRepresentation.Type.MERMAID, "Cursor implementation graph").getContent();
    }

    private void run(GraphInput input, RunnableConfig config) {
        graph.invoke(input, config);
    }

    private static RunnableConfig threadConfig(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }

    private RunnableConfig requireThread(String threadId) {
        return Optional.ofNullable(threads.get(threadId))
                .orElseThrow(() -> new TaskNotFoundException(threadId));
    }

    /**
     * Snapshot returned to the API and UI.
     */
    public record TaskView(
            String threadId,
            TaskStatus status,
            String brief,
            String prompt,
            String decision,
            String repoUrl,
            String cursorAgentId,
            String cursorRunId,
            String cursorUrl,
            String summary,
            boolean verifyPassed,
            String verifyOutput,
            int retryCount,
            String implementerMode,
            List<String> steps) {

        static TaskView from(String threadId, TaskState state) {
            return new TaskView(
                    threadId,
                    state.status(),
                    state.brief(),
                    state.prompt(),
                    state.decision(),
                    state.repoUrl(),
                    state.agentId(),
                    state.runId(),
                    state.agentUrl(),
                    state.summary(),
                    state.verifyPassed(),
                    state.verifyOutput(),
                    state.retryCount(),
                    state.implementerMode(),
                    new ArrayList<>(state.steps()));
        }
    }

    public static final class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(String threadId) {
            super("Unknown implementation thread");
        }
    }
}
