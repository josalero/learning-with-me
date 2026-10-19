package dev.mytechprofile.langgraph4j.graph;

import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;

import dev.mytechprofile.langgraph4j.cursor.ImplementationPort;
import dev.mytechprofile.langgraph4j.domain.FeatureBriefPlanner;
import dev.mytechprofile.langgraph4j.domain.ImplementationRequest;
import dev.mytechprofile.langgraph4j.domain.ImplementationResult;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

/**
 * Node actions for plan, approve, implement, and verify.
 */
public final class ImplementationNodes {

    private ImplementationNodes() {
    }

    public static NodeAction<TaskState> plan() {
        return state -> Map.of(
                TaskState.PROMPT, FeatureBriefPlanner.plan(state.brief()),
                TaskState.STATUS, TaskStatus.WAITING_APPROVAL.name(),
                TaskState.STEPS, "plan: built Cursor prompt");
    }

    public static NodeAction<TaskState> approve() {
        return state -> {
            if (state.rejected()) {
                return Map.of(
                        TaskState.STATUS, TaskStatus.REJECTED.name(),
                        TaskState.STEPS, "approve: human rejected the prompt");
            }
            return Map.of(
                    TaskState.STATUS, TaskStatus.IMPLEMENTING.name(),
                    TaskState.STEPS, "approve: human approved the prompt");
        };
    }

    public static NodeAction<TaskState> implement(ImplementationPort port) {
        return state -> {
            ImplementationResult result = port.implement(new ImplementationRequest(
                    state.threadId(),
                    state.prompt(),
                    state.repoUrl(),
                    state.startingRef()));
            return Map.of(
                    TaskState.AGENT_ID, nullToEmpty(result.agentId()),
                    TaskState.RUN_ID, nullToEmpty(result.runId()),
                    TaskState.AGENT_URL, nullToEmpty(result.agentUrl()),
                    TaskState.SUMMARY, nullToEmpty(result.summary()),
                    TaskState.VERIFY_PASSED, result.passed(),
                    TaskState.VERIFY_OUTPUT, nullToEmpty(result.verifyOutput()),
                    TaskState.MODE, nullToEmpty(result.mode()),
                    TaskState.STATUS, TaskStatus.VERIFYING.name(),
                    TaskState.STEPS, "implement: " + result.mode() + " " + shortSummary(result));
        };
    }

    public static NodeAction<TaskState> verify() {
        return state -> {
            if (state.verifyPassed()) {
                return Map.of(
                        TaskState.STATUS, TaskStatus.DONE.name(),
                        TaskState.STEPS, "verify: passed");
            }
            if (state.canRetry()) {
                return Map.of(
                        TaskState.RETRY_COUNT, state.retryCount() + 1,
                        TaskState.STATUS, TaskStatus.IMPLEMENTING.name(),
                        TaskState.STEPS, "verify: failed, retry " + (state.retryCount() + 1));
            }
            return Map.of(
                    TaskState.STATUS, TaskStatus.FAILED.name(),
                    TaskState.STEPS, "verify: failed, no retries left");
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String shortSummary(ImplementationResult result) {
        String summary = result.summary() == null ? "" : result.summary();
        return summary.length() > 80 ? summary.substring(0, 80) + "…" : summary;
    }
}
