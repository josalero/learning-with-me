package dev.mytechprofile.sdlc.adapter;

import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import dev.mytechprofile.sdlc.port.RunStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local run store. Fine for a learning POC; swap for a DB later.
 *
 * <p>Sample: {@code store.save(outcome); store.find(runId)}.
 */
public final class InMemoryRunStore implements RunStore {

    private final Map<String, RunOutcome> runs = new ConcurrentHashMap<>();

    @Override
    public void save(RunOutcome outcome) {
        runs.put(outcome.runId(), outcome);
    }

    @Override
    public Optional<RunOutcome> find(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<RunOutcome> findAll() {
        return new ArrayList<>(runs.values());
    }

    @Override
    public void updateStatus(String runId, RunStatus status) {
        RunOutcome current = runs.get(runId);
        if (current == null) {
            return;
        }
        runs.put(runId, current.withStatus(status));
    }
}
