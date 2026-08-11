package dev.mytechprofile.research.springai.orchestration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.mytechprofile.research.springai.domain.ResearchRole;
import dev.mytechprofile.research.springai.domain.StepEvent;

/**
 * Collects per-agent step events for the report and optional SSE subscribers.
 */
public final class StepTrace {

    private final List<StepEvent> steps = new CopyOnWriteArrayList<>();
    private final List<Consumer<StepEvent>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<StepEvent> listener) {
        listeners.add(listener);
    }

    public void record(ResearchRole role, String status, String input, String output, long elapsedMs) {
        StepEvent event = new StepEvent(role.wireName(), status, input, output, elapsedMs);
        steps.add(event);
        for (Consumer<StepEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    public List<StepEvent> steps() {
        return List.copyOf(steps);
    }
}
