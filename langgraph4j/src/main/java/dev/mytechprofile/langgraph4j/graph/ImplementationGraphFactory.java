package dev.mytechprofile.langgraph4j.graph;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.Map;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import dev.mytechprofile.langgraph4j.cursor.ImplementationPort;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

/**
 * Builds the plan → approve (interrupt) → implement → verify graph.
 */
public final class ImplementationGraphFactory {

    public static final String PLAN = "plan";
    public static final String APPROVE = "approve";
    public static final String IMPLEMENT = "cursor_implement";
    public static final String VERIFY = "verify";

    private ImplementationGraphFactory() {
    }

    /**
     * Compiles a checkpointed graph that pauses after {@code plan}, before {@code approve}.
     *
     * <pre>{@code
     * CompiledGraph<TaskState> graph = ImplementationGraphFactory.compile(port, saver);
     * }</pre>
     */
    public static CompiledGraph<TaskState> compile(ImplementationPort port, MemorySaver saver)
            throws GraphStateException {
        StateGraph<TaskState> workflow = new StateGraph<>(TaskState.SCHEMA, TaskState::new)
                .addNode(PLAN, node_async(ImplementationNodes.plan()))
                .addNode(APPROVE, node_async(ImplementationNodes.approve()))
                .addNode(IMPLEMENT, node_async(ImplementationNodes.implement(port)))
                .addNode(VERIFY, node_async(ImplementationNodes.verify()))
                .addEdge(START, PLAN)
                .addEdge(PLAN, APPROVE)
                .addConditionalEdges(
                        APPROVE,
                        edge_async(state -> state.rejected() ? "rejected" : "implement"),
                        Map.of("rejected", END, "implement", IMPLEMENT))
                .addEdge(IMPLEMENT, VERIFY)
                .addConditionalEdges(
                        VERIFY,
                        edge_async(state -> {
                            if (state.verifyPassed()) {
                                return "done";
                            }
                            return state.status() == TaskStatus.IMPLEMENTING ? "retry" : "failed";
                        }),
                        Map.of("done", END, "retry", IMPLEMENT, "failed", END));

        CompileConfig config = CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptAfter(PLAN)
                .build();
        return workflow.compile(config);
    }
}
