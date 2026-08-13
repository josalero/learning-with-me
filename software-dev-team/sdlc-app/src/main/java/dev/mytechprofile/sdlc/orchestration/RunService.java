package dev.mytechprofile.sdlc.orchestration;

import dev.mytechprofile.sdlc.catalog.CatalogException;
import dev.mytechprofile.sdlc.config.SdlcProperties;
import dev.mytechprofile.sdlc.domain.RunCommand;
import dev.mytechprofile.sdlc.domain.RunOutcome;
import dev.mytechprofile.sdlc.domain.RunStatus;
import dev.mytechprofile.sdlc.domain.StakeholderDecision;
import dev.mytechprofile.sdlc.domain.StepEvent;
import dev.mytechprofile.sdlc.port.ArtifactStore;
import dev.mytechprofile.sdlc.port.RunStore;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts feature runs on virtual threads and records live steps for SSE.
 *
 * <p><strong>When to use:</strong> {@code POST /api/v1/runs}. Callers poll {@link #get(String)} or
 * subscribe to {@link #subscribe(String, Consumer)}.
 *
 * <p><strong>Example:</strong> {@code service.start(new RunCommand(team, project, feature))}.
 */
public final class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final SdlcOrchestrator orchestrator;
    private final RunStore runs;
    private final ArtifactStore artifacts;
    private final HumanApprovalGate approvalGate;
    private final SdlcProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final RunEventHub events = new RunEventHub();

    /**
     * Creates the run facade.
     *
     * @param orchestrator pipeline
     * @param runs run store
     * @param artifacts artifact store
     * @param approvalGate human stakeholder
     * @param properties OpenRouter key check
     */
    public RunService(
            SdlcOrchestrator orchestrator,
            RunStore runs,
            ArtifactStore artifacts,
            HumanApprovalGate approvalGate,
            SdlcProperties properties) {
        this.orchestrator = orchestrator;
        this.runs = runs;
        this.artifacts = artifacts;
        this.approvalGate = approvalGate;
        this.properties = properties;
    }

    /**
     * Queues a new run and returns the pending record.
     *
     * @param command team, project, feature
     * @return pending outcome with generated id
     */
    public RunOutcome start(RunCommand command) {
        if (!properties.offline()
                && (properties.openrouterApiKey() == null
                        || properties.openrouterApiKey().isBlank())) {
            throw new CatalogException(
                    "OPENROUTER_API_KEY is not set. Copy .env.example to .env and add a key from https://openrouter.ai/keys, or set SDLC_OFFLINE=true for a canned walkthrough");
        }
        String runId = UUID.randomUUID().toString();
        RunOutcome pending = RunOutcome.pending(runId, command);
        runs.save(pending);
        artifacts.writeOutcome(pending);
        executor.submit(() -> execute(runId, command));
        return pending;
    }

    /**
     * Returns one run.
     *
     * @param runId identifier
     * @return outcome
     */
    public RunOutcome get(String runId) {
        return runs.find(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown run '" + runId + "'. Check Results."));
    }

    /**
     * Lists runs newest first.
     *
     * @return outcomes
     */
    public List<RunOutcome> list() {
        return runs.findAll().stream()
                .sorted(Comparator.comparing(RunOutcome::startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Registers a live step consumer and replays steps already stored.
     *
     * @param runId run identifier
     * @param listener consumer
     * @return subscription that must be closed
     */
    public AutoCloseable subscribe(String runId, Consumer<StepEvent> listener) {
        get(runId);
        for (StepEvent step : artifacts.steps(runId)) {
            listener.accept(step);
        }
        return events.subscribe(runId, listener);
    }

    /**
     * Completes a human stakeholder wait.
     *
     * @param runId run in {@link RunStatus#WAITING_APPROVAL}
     * @param decision operator decision
     * @return updated outcome
     */
    public RunOutcome approve(String runId, StakeholderDecision decision) {
        RunOutcome current = get(runId);
        if (current.status() != RunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException(
                    "Run " + runId + " is " + current.status() + ", not WAITING_APPROVAL. Refresh Results.");
        }
        boolean accepted = approvalGate.complete(runId, decision);
        if (!accepted) {
            throw new IllegalStateException("No human waiter for run " + runId + ". The pipeline may have timed out.");
        }
        RunOutcome updated = current.withProducts(
                current.branch(),
                current.featureBrief(),
                current.aiSpec(),
                current.changeSummary(),
                current.buildResult(),
                current.reviewVerdict(),
                current.qaVerdict(),
                decision);
        runs.save(updated);
        artifacts.writeJson(runId, "08-stakeholder-decision.json", decision);
        artifacts.writeOutcome(updated);
        return updated;
    }

    /**
     * Lists artifact file names for a run.
     *
     * @param runId identifier
     * @return file names
     */
    public List<String> artifacts(String runId) {
        get(runId);
        return artifacts.listFiles(runId);
    }

    /**
     * Reads one artifact.
     *
     * @param runId identifier
     * @param fileName plain file name
     * @return text
     */
    public String artifact(String runId, String fileName) {
        get(runId);
        return artifacts.readText(runId, fileName);
    }

    private void execute(String runId, RunCommand command) {
        try {
            runs.updateStatus(runId, RunStatus.RUNNING);
            runs.find(runId).ifPresent(artifacts::writeOutcome);
            orchestrator.run(runId, command, event -> events.publish(runId, event));
        } catch (RuntimeException ex) {
            log.error("Run {} crashed: {}", runId, ex.getMessage());
            runs.find(runId).ifPresent(outcome -> {
                RunOutcome failed = outcome.failed(ex.getMessage());
                runs.save(failed);
                artifacts.writeOutcome(failed);
            });
        }
    }
}
