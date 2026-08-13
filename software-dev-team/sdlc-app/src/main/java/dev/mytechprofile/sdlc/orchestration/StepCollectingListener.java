package dev.mytechprofile.sdlc.orchestration;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.mytechprofile.sdlc.domain.StepEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Records every agent invocation for SSE and {@code run.json}.
 *
 * <p><strong>When to use:</strong> attach to {@code sequenceBuilder().listener(listener)}.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * listener.addListener(event -> sse.send(event));
 * }</pre>
 */
public final class StepCollectingListener implements AgentListener {

    private static final int MAX_TEXT_CHARS = 8_000;

    private final ConcurrentHashMap<String, Long> startedAtByAgentId = new ConcurrentHashMap<>();
    private final List<StepEvent> steps = new CopyOnWriteArrayList<>();
    private final List<Consumer<StepEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a live consumer of step events.
     *
     * @param listener callback invoked after each agent completes
     */
    public void addListener(Consumer<StepEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Returns a snapshot of recorded steps.
     *
     * @return immutable copy
     */
    public List<StepEvent> steps() {
        return List.copyOf(steps);
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        startedAtByAgentId.put(request.agentId(), System.currentTimeMillis());
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        Long start = startedAtByAgentId.remove(response.agentId());
        long elapsed = start == null ? 0L : System.currentTimeMillis() - start;
        StepEvent event = new StepEvent(
                response.agentName(),
                "completed",
                formatInputs(response.inputs()),
                formatValue(response.output()),
                elapsed);
        steps.add(event);
        for (Consumer<StepEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private static String formatInputs(Map<String, Object> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "(no inputs)";
        }
        return truncate(inputs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + formatValue(entry.getValue()))
                .collect(Collectors.joining("; ")));
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        return truncate(value.toString());
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TEXT_CHARS - 3) + "...";
    }
}
