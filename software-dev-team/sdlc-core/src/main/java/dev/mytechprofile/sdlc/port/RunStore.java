package dev.mytechprofile.sdlc.port;

import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import java.util.List;
import java.util.Optional;

/**
 * In-memory or durable store of run records.
 *
 * <p>Sample: {@code store.save(outcome)} then {@code store.find(runId)}.
 */
public interface RunStore {

    void save(RunOutcome outcome);

    Optional<RunOutcome> find(String runId);

    List<RunOutcome> findAll();

    void updateStatus(String runId, RunStatus status);
}
