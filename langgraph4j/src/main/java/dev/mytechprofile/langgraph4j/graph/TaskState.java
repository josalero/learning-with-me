package dev.mytechprofile.langgraph4j.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import dev.mytechprofile.langgraph4j.domain.Decision;
import dev.mytechprofile.langgraph4j.domain.TaskStatus;

/**
 * Shared LangGraph4j state for one implementation thread.
 */
public final class TaskState extends AgentState {

    public static final String THREAD_ID = "threadId";
    public static final String BRIEF = "brief";
    public static final String REPO_URL = "repoUrl";
    public static final String STARTING_REF = "startingRef";
    public static final String PROMPT = "prompt";
    public static final String DECISION = "decision";
    public static final String STATUS = "status";
    public static final String AGENT_ID = "cursorAgentId";
    public static final String RUN_ID = "cursorRunId";
    public static final String AGENT_URL = "cursorUrl";
    public static final String SUMMARY = "implementationSummary";
    public static final String VERIFY_PASSED = "verifyPassed";
    public static final String VERIFY_OUTPUT = "verifyOutput";
    public static final String RETRY_COUNT = "retryCount";
    public static final String MAX_RETRIES = "maxRetries";
    public static final String MODE = "implementerMode";
    public static final String STEPS = "steps";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(THREAD_ID, Channels.base(() -> "")),
            Map.entry(BRIEF, Channels.base(() -> "")),
            Map.entry(REPO_URL, Channels.base(() -> "")),
            Map.entry(STARTING_REF, Channels.base(() -> "main")),
            Map.entry(PROMPT, Channels.base(() -> "")),
            Map.entry(DECISION, Channels.base(() -> "")),
            Map.entry(STATUS, Channels.base(TaskStatus.WAITING_APPROVAL::name)),
            Map.entry(AGENT_ID, Channels.base(() -> "")),
            Map.entry(RUN_ID, Channels.base(() -> "")),
            Map.entry(AGENT_URL, Channels.base(() -> "")),
            Map.entry(SUMMARY, Channels.base(() -> "")),
            Map.entry(VERIFY_PASSED, Channels.base(() -> Boolean.FALSE)),
            Map.entry(VERIFY_OUTPUT, Channels.base(() -> "")),
            Map.entry(RETRY_COUNT, Channels.base(() -> 0)),
            Map.entry(MAX_RETRIES, Channels.base(() -> 1)),
            Map.entry(MODE, Channels.base(() -> "STUB")),
            Map.entry(STEPS, Channels.appender(ArrayList::new)));

    public TaskState(Map<String, Object> initData) {
        super(initData);
    }

    public String threadId() {
        return stringValue(THREAD_ID);
    }

    public String brief() {
        return stringValue(BRIEF);
    }

    public String repoUrl() {
        return stringValue(REPO_URL);
    }

    public String startingRef() {
        String ref = stringValue(STARTING_REF);
        return ref.isBlank() ? "main" : ref;
    }

    public String prompt() {
        return stringValue(PROMPT);
    }

    public String decision() {
        return stringValue(DECISION);
    }

    public boolean rejected() {
        return Decision.REJECT.name().equals(decision());
    }

    public TaskStatus status() {
        return this.<String>value(STATUS)
                .map(TaskStatus::valueOf)
                .orElse(TaskStatus.WAITING_APPROVAL);
    }

    public String agentId() {
        return stringValue(AGENT_ID);
    }

    public String runId() {
        return stringValue(RUN_ID);
    }

    public String agentUrl() {
        return stringValue(AGENT_URL);
    }

    public String summary() {
        return stringValue(SUMMARY);
    }

    public boolean verifyPassed() {
        return this.<Boolean>value(VERIFY_PASSED).orElse(Boolean.FALSE);
    }

    public String verifyOutput() {
        return stringValue(VERIFY_OUTPUT);
    }

    public int retryCount() {
        return this.<Number>value(RETRY_COUNT).map(Number::intValue).orElse(0);
    }

    public int maxRetries() {
        return this.<Number>value(MAX_RETRIES).map(Number::intValue).orElse(1);
    }

    public String implementerMode() {
        return stringValue(MODE);
    }

    @SuppressWarnings("unchecked")
    public List<String> steps() {
        return this.<List<String>>value(STEPS).orElse(List.of());
    }

    public boolean canRetry() {
        return !verifyPassed() && retryCount() < maxRetries();
    }

    private String stringValue(String key) {
        return this.<Object>value(key).map(Object::toString).orElse("");
    }
}
