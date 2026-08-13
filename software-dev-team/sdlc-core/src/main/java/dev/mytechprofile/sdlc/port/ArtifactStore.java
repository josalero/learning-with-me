package dev.mytechprofile.sdlc.port;

import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.StepEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * Persists numbered run artifacts under {@code runs/<runId>/}.
 *
 * <p>Sample: {@code store.writeJson(runId, "01-feature-brief.json", brief)}.
 */
public interface ArtifactStore {

    Path runDirectory(String runId);

    void writeJson(String runId, String fileName, Object value);

    void writeText(String runId, String fileName, String text);

    void writeOutcome(RunOutcome outcome);

    List<StepEvent> appendStep(String runId, StepEvent event);

    /**
     * Returns steps already recorded for a run.
     *
     * @param runId run identifier
     * @return recorded steps
     */
    List<StepEvent> steps(String runId);

    /**
     * Lists artifact file names for a run.
     *
     * @param runId run identifier
     * @return sorted file names
     */
    List<String> listFiles(String runId);

    /**
     * Reads an artifact as UTF-8 text.
     *
     * @param runId run identifier
     * @param fileName file in the run directory (no path separators)
     * @return file contents
     */
    String readText(String runId, String fileName);
}
