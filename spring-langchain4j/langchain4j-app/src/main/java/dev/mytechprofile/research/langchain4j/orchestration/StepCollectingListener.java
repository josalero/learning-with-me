package dev.mytechprofile.research.langchain4j.orchestration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.mytechprofile.research.langchain4j.domain.ResearchRole;
import dev.mytechprofile.research.langchain4j.domain.StepEvent;

/**
 * Records per-agent invocations for SSE and the final report.
 *
 * <p>Elapsed timing is keyed by {@link AgentRequest#agentId()} so nested sequence/loop
 * agents (and parallel agents) do not clobber each other's start times.
 */
public final class StepCollectingListener implements AgentListener {

    private static final int MAX_TEXT_CHARS = 12_000;

    private final ConcurrentHashMap<String, Long> startedAtByAgentId = new ConcurrentHashMap<>();
    private final List<StepEvent> steps = new CopyOnWriteArrayList<>();
    private final List<Consumer<StepEvent>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<StepEvent> listener) {
        listeners.add(listener);
    }

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

        ResearchRole role = ResearchRole.fromAgentName(response.agentName()).orElse(null);
        if (role == null) {
            return;
        }
        StepEvent event = new StepEvent(
                role.wireName(),
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
                .map(entry -> entry.getKey() + ":\n" + formatValue(entry.getValue()))
                .collect(Collectors.joining("\n\n")));
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
