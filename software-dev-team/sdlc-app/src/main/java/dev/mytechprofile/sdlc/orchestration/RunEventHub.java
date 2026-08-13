package dev.mytechprofile.sdlc.orchestration;

import dev.mytechprofile.sdlc.domain.StepEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fan-out of step events to SSE subscribers.
 *
 * <p>Sample: {@code hub.subscribe(runId, emitter::send)}.
 */
final class RunEventHub {

    private static final Logger log = LoggerFactory.getLogger(RunEventHub.class);

    private final Map<String, List<Consumer<StepEvent>>> listeners = new ConcurrentHashMap<>();

    AutoCloseable subscribe(String runId, Consumer<StepEvent> listener) {
        listeners.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> {
            List<Consumer<StepEvent>> list = listeners.get(runId);
            if (list != null) {
                list.remove(listener);
            }
        };
    }

    void publish(String runId, StepEvent event) {
        List<Consumer<StepEvent>> list = listeners.get(runId);
        if (list == null) {
            return;
        }
        for (Consumer<StepEvent> listener : list) {
            deliver(runId, event, listener);
        }
    }

    /**
     * A dead browser tab must never fail an agent invocation, so delivery failures only unsubscribe.
     */
    private void deliver(String runId, StepEvent event, Consumer<StepEvent> listener) {
        try {
            listener.accept(event);
        } catch (RuntimeException ex) {
            log.debug("Dropping SSE subscriber for run {}: {}", runId, ex.getMessage());
            List<Consumer<StepEvent>> list = listeners.get(runId);
            if (list != null) {
                list.remove(listener);
            }
        }
    }
}
